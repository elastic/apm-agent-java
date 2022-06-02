/*
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
 */
package co.elastic.apm.agent.log.shipper;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;

public class FileTailer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(FileTailer.class);
    private final List<TailableFile> tailableFiles;
    private final FileChangeListener fileChangeListener;
    private final ByteBuffer buffer;
    private final int maxLinesPerCycle;
    private volatile boolean stopRequested = false;
    private final long idleTimeMs;
    private final Thread processingThread;

    public FileTailer(FileChangeListener fileChangeListener, int bufferSize, int maxLinesPerCycle, long idleTimeMs, ThreadFactory processingThreadFactory) {
        this.tailableFiles = new CopyOnWriteArrayList<>();
        this.fileChangeListener = fileChangeListener;
        this.buffer = ByteBuffer.allocate(bufferSize);
        this.maxLinesPerCycle = maxLinesPerCycle;
        this.idleTimeMs = idleTimeMs;
        this.processingThread = processingThreadFactory.newThread(this);
    }

    public TailableFile tailFile(File file) throws IOException {
        TailableFile tailableFile = new TailableFile(file);
        tailableFiles.add(tailableFile);
        return tailableFile;
    }

    public void start() {
        processingThread.start();
    }

    public void stop(long timeout) throws Exception {
        stopRequested = true;
        fileChangeListener.onShutdownInitiated();
        processingThread.join(timeout);
    }

    @Override
    public void run() {
        try {
            while (!stopRequested) {
                int readLines = pollAll();
                if (readLines == 0) {
                    fileChangeListener.onIdle();
                    Thread.sleep(idleTimeMs);
                }
            }
            pollAll();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        fileChangeListener.onShutdownComplete();
    }

    private int pollAll() {
        int lines = 0;
        for (TailableFile tailableFile : tailableFiles) {
            try {
                lines += tailableFile.tail(buffer, fileChangeListener, maxLinesPerCycle);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
        return lines;
    }

}
