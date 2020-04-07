package co.elastic.apm.log.shipper;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class MonitoredFile {
    private static final byte NEW_LINE = (byte) '\n';
    @Nullable
    private FileChannel fileChannel;
    private final File file;

    public MonitoredFile(File file) {
        this.file = file;
    }

    public int poll(ByteBuffer buffer, FileChangeListener listener, int maxLines) throws IOException {
        int readLines = 0;
        while (readLines < maxLines) {
            FileChannel currentFile = getFileChannel();
            if (currentFile == null || isFullyRead()) {
                return readLines;
            }
            readLines += readFile(buffer, listener, maxLines - readLines, currentFile);
        }
        return readLines;
    }

    private int readFile(ByteBuffer buffer, FileChangeListener listener, int maxLines, FileChannel currentFile) throws IOException {
        int readLines = 0;
        while (readLines < maxLines) {
            buffer.clear();
            int read = currentFile.read(buffer);
            if (read != -1) {
                buffer.flip();
                byte[] array = buffer.array();
                readLines += readLines(file, array, read, maxLines - readLines, listener);
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
        FileChannel newFileChannel = FileChannel.open(file.toPath());
        if (this.fileChannel != null) {
            this.fileChannel.close();
        }
        this.fileChannel = newFileChannel;
    }

    static int readLines(File file, byte[] buffer, int bufferLimit, int maxLines, FileChangeListener listener) {
        int lines = 0;
        int currentLineStartOffset = 0;
        while (lines < maxLines) {
            int indexOfNewLine = indexOf(buffer, NEW_LINE, currentLineStartOffset, bufferLimit);
            if (indexOfNewLine != -1) {
                int length = indexOfNewLine - currentLineStartOffset;
                // for Windows-style \r\n line endings
                if (indexOfNewLine > 0 && buffer[indexOfNewLine - 1] == '\r') {
                    length--;
                }
                listener.onLineAvailable(file, buffer, currentLineStartOffset, length, true);
                currentLineStartOffset = indexOfNewLine + 1;
                lines++;
            } else {
                int length = bufferLimit - currentLineStartOffset;
                if (length > 0) {
                    listener.onLineAvailable(file, buffer, currentLineStartOffset, length, false);
                }
                return lines;
            }
        }
        return lines;
    }

    static int indexOf(byte[] bytes, byte b, int offset, int limit) {
        for (int i = offset; i < limit; i++) {
            if (bytes[i] == b) {
                return i;
            }
        }
        return -1;
    }
}
