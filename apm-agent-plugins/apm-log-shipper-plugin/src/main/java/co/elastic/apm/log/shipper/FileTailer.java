package co.elastic.apm.log.shipper;

import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class FileTailer implements LifecycleListener, Runnable {

    private static final Logger logger = LoggerFactory.getLogger(FileTailer.class);
    private final List<MonitoredFile> monitoredFiles;
    private final FileChangeListener fileChangeListener;
    private final ByteBuffer buffer;
    private final int maxLinesPerCycle;
    private volatile boolean stopRequested = false;
    private final long idleTimeMs;

    public FileTailer(List<MonitoredFile> monitoredFiles, FileChangeListener fileChangeListener, int bufferSize, int maxLinesPerCycle, long idleTimeMs) {
        this.monitoredFiles = monitoredFiles;
        this.fileChangeListener = fileChangeListener;
        this.buffer = ByteBuffer.allocate(bufferSize);
        this.maxLinesPerCycle = maxLinesPerCycle;
        this.idleTimeMs = idleTimeMs;
    }

    @Override
    public void start(ElasticApmTracer tracer) throws Exception {

    }

    @Override
    public void pause() throws Exception {

    }

    @Override
    public void resume() throws Exception {

    }

    @Override
    public void stop() throws Exception {
        stopRequested = true;
    }

    @Override
    public void run() {
        while (!stopRequested) {
            int readLines = pollAll();
            if (readLines == 0) {
                try {
                    fileChangeListener.onIdle();
                    Thread.sleep(idleTimeMs);
                } catch (InterruptedException e) {
                    stopRequested = true;
                }
            }
        }
        pollAll();
    }

    private int pollAll() {
        int lines = 0;
        for (MonitoredFile monitoredFile : monitoredFiles) {
            try {
                lines += monitoredFile.poll(buffer, fileChangeListener, maxLinesPerCycle);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return lines;
    }

}
