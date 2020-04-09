/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.log.shipper;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public class MonitoredFile implements Closeable {
    private static final byte NEW_LINE = (byte) '\n';
    private static final int EOF = -1;
    private final File file;
    private final File stateFile;
    private final FileChannel stateFileChannel;
    private final FileLock stateFileLock;
    @Nullable
    private FileChannel fileChannel;
    /**
     * The creation date of the file.
     * The name of the file might change as its being read due to file rotation.
     * The file creation time can help to identify which file to read from when restoring the state.
     */
    private long fileCreationTime;

    public MonitoredFile(File file) throws IOException {
        this.file = file;
        stateFile = new File(file + ".state");
        stateFileChannel = FileChannel.open(stateFile.toPath(), CREATE, READ, WRITE);
        stateFileLock = stateFileChannel.tryLock();
        if (stateFileLock == null) {
            throw new IllegalStateException("This file is currently locked by another process: " + stateFile);
        }
        readState();
    }

    public void readState() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(stateFile)) {
            properties.load(input);
            restoreState(properties);
        } catch (FileNotFoundException e) {
            openFile();
        }
    }

    public void deleteState() {
        new File(file + ".state").delete();
    }

    private void restoreState(Properties state) throws IOException {
        long position = Long.parseLong(state.getProperty("position", "0"));
        long creationTime = Long.parseLong(state.getProperty("creationTime", "0"));
        if (getCreationTime(file.toPath()) == creationTime) {
            openExistingFile(position, file.toPath());
        } else {
            // the file has rotated and is now named differently, maybe something like file-0.log
            // let's search in the same directory for a file with the creation time from the state file
            File rotatedFile = findFileWithCreationDate(file.getParentFile(), creationTime);
            if (rotatedFile != null && rotatedFile.length() > position) {
                openExistingFile(position, rotatedFile.toPath());
            }
        }
    }

    private void saveState() throws IOException {
        if (fileChannel == null) {
            return;
        }
        Properties properties = new Properties();
        properties.put("position", Long.toString(fileChannel.position()));
        properties.put("creationTime", Long.toString(fileCreationTime));
        try (FileOutputStream os = new FileOutputStream(stateFile)) {
            properties.store(os, null);
        }
    }

    @Nullable
    private File findFileWithCreationDate(File dir, final long creationTime) {
        File[] files = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                try {
                    return getCreationTime(file.toPath()) == creationTime;
                } catch (IOException e) {
                    return false;
                }
            }
        });
        if (files != null && files.length == 1) {
            return files[0];
        } else {
            return null;
        }
    }

    private long getCreationTime(Path path) throws IOException {
        BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
        return attr.creationTime().to(TimeUnit.MILLISECONDS);
    }

    public int poll(ByteBuffer buffer, FileChangeListener listener, int maxLines) throws IOException {
        int readLines = 0;
        while (readLines < maxLines) {
            FileChannel currentFile = getFileChannel();
            if (currentFile == null || isFullyRead()) {
                return readLines;
            }
            readLines += readFile(buffer, listener, maxLines - readLines, currentFile);
            saveState();
        }
        return readLines;
    }

    private int readFile(ByteBuffer buffer, FileChangeListener listener, int maxLines, FileChannel currentFile) throws IOException {
        int readLines = 0;
        while (readLines < maxLines) {
            buffer.clear();
            int read = currentFile.read(buffer);
            if (read != EOF) {
                buffer.flip();
                readLines += readLines(file, buffer, maxLines - readLines, listener);
                currentFile.position(currentFile.position() - buffer.remaining());
            } else {
                // assumes EOF equals EOL
                // this might be wrong when another process is in the middle of writing the line
                // (when is the new file size visible to other processes?)
                return readLines;
            }
        }
        return readLines;
    }

    @Nullable
    private FileChannel getFileChannel() throws IOException {
        if (fileChannel == null || (isFullyRead() && hasRotated())) {
            openFile();
        }
        return fileChannel;
    }

    private boolean hasRotated() throws IOException {
        return fileChannel != null && file.exists() && file.length() < fileChannel.position();
    }

    private boolean isFullyRead() throws IOException {
        return fileChannel != null && fileChannel.position() == fileChannel.size();
    }

    private void openFile() throws IOException {
        if (!file.exists()) {
            return;
        }
        openExistingFile(0, file.toPath());
    }

    private void openExistingFile(long position, Path path) throws IOException {
        if (this.fileChannel != null) {
            this.fileChannel.close();
        }
        fileChannel = FileChannel.open(path);
        fileChannel.position(position);
        fileCreationTime = getCreationTime(file.toPath());
    }

    static int readLines(File file, ByteBuffer buffer, int maxLines, FileChangeListener listener) throws IOException {
        int lines = 0;
        while (buffer.hasRemaining() && lines < maxLines) {
            int startPos = buffer.position();
            boolean hasNewLine = skipUntil(buffer, NEW_LINE);
            int length = buffer.position() - startPos;
            if (hasNewLine) {
                length--;
                // for Windows-style \r\n line endings
                if (length > 0 && buffer.get(buffer.position() - 2) == '\r') {
                    length--;
                }
                lines++;
            }
            if (length > 0) {
                while (!listener.onLineAvailable(file, buffer.array(), startPos, length, hasNewLine)) ;
            }
        }
        return lines;
    }

    static boolean skipUntil(ByteBuffer bytes, byte b) {
        while (bytes.hasRemaining()) {
            if (bytes.get() == b) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        IOException exception = null;
        try {
            stateFileLock.release();
        } catch (IOException e) {
            exception = e;
        }
        try {
            stateFileChannel.close();
        } catch (IOException e) {
            if (exception != null) {
                e.addSuppressed(exception);
            }
            exception = e;
        }
        try {
            if (fileChannel != null) {
                fileChannel.close();
            }
        } catch (IOException e) {
            if (exception != null) {
                e.addSuppressed(exception);
            }
            exception = e;
        }
        if (exception != null) {
            throw exception;
        }
    }
}
