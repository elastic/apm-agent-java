package co.elastic.apm.report;

import co.elastic.apm.impl.MetaData;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.report.processor.ProcessorEventHandler;
import co.elastic.apm.report.serialize.DslJsonSerializer;
import co.elastic.apm.report.serialize.PayloadSerializer;
import co.elastic.apm.util.VersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.util.IOUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * This reporter supports the nd-json HTTP streaming based /v2/intake protocol
 */
// TODO support verify_server_cert, api_request_size, api_request_time
public class IntakeV2ReportingEventHandler implements ReportingEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(IntakeV2ReportingEventHandler.class);
    private static final int GZIP_COMPRESSION_LEVEL = 1;

    private final ReporterConfiguration reporterConfiguration;
    private final ProcessorEventHandler processorEventHandler;
    private final MetaData metaData;
    private final PayloadSerializer payloadSerializer;
    private Deflater deflater;
    private long currentlyTransmitting = 0;
    private long reported = 0;
    @Nullable
    private HttpURLConnection connection;
    @Nullable
    private OutputStream os;

    IntakeV2ReportingEventHandler(Service service, ProcessInfo process, SystemInfo system,
                                  ReporterConfiguration reporterConfiguration, ProcessorEventHandler processorEventHandler,
                                  PayloadSerializer payloadSerializer) {
        this.reporterConfiguration = reporterConfiguration;
        this.processorEventHandler = processorEventHandler;
        this.payloadSerializer = payloadSerializer;
        this.metaData = new MetaData(process, service, system);
        this.deflater = new Deflater(GZIP_COMPRESSION_LEVEL);
    }

    @Override
    public void onEvent(ReportingEvent event, long sequence, boolean endOfBatch) throws IOException {
        if (logger.isTraceEnabled()) {
            logger.trace("Receiving {} event (sequence {})", event.getType(), sequence);
        }
        if (event.getType() == null) {
            return;
        } else if (event.getType() == ReportingEvent.ReportingEventType.FLUSH) {
            flush();
            return;
        }
        processorEventHandler.onEvent(event, sequence, endOfBatch);
        if (connection == null) {
            connection = startRequest();
            payloadSerializer.serializeMetaDataNdJson(metaData);
        }
        if (event.getTransaction() != null) {
            currentlyTransmitting++;
            payloadSerializer.serializeTransactionNdJson(event.getTransaction());
            event.getTransaction().recycle();
        } else if (event.getSpan() != null) {
            currentlyTransmitting++;
            payloadSerializer.serializeSpanNdJson(event.getSpan());
            event.getSpan().recycle();
        } else if (event.getError() != null) {
            currentlyTransmitting++;
            payloadSerializer.serializeErrorNdJson(event.getError());
            event.getError().recycle();
        }
        event.resetState();
        if (shouldFlush()) {
            flush();
        }
    }

    private boolean shouldFlush() {
        return deflater.getBytesRead() + DslJsonSerializer.BUFFER_SIZE >= 10 * 1024 * 1024;
    }

    private HttpURLConnection startRequest() throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Starting new request");
        }
        URL url = new URL(reporterConfiguration.getServerUrl() + "/v2/intake");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        if (reporterConfiguration.getSecretToken() != null) {
            connection.setRequestProperty("Authorization", "Bearer " + reporterConfiguration.getSecretToken());
        }
        connection.setChunkedStreamingMode(DslJsonSerializer.BUFFER_SIZE);
        connection.setRequestProperty("User-Agent", "java-agent/" + VersionUtils.getAgentVersion());
        connection.setRequestProperty("Content-Encoding", "deflate");
        connection.setRequestProperty("Content-Type", "application/x-ndjson");
        connection.setUseCaches(false);
        connection.connect();
        os = new DeflaterOutputStream(connection.getOutputStream(), deflater);
        payloadSerializer.setOutputStream(os);
        return connection;
    }

    void flush() {
        if (connection != null) {
            try {
                payloadSerializer.flush();
                if (os != null) {
                    os.close();
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Flushing {} uncompressed {} compressed bytes", deflater.getBytesRead(), deflater.getBytesWritten());
                }
                InputStream inputStream = connection.getInputStream();
                final int responseCode = connection.getResponseCode();
                if (responseCode >= 400) {
                    onFlushError(responseCode, inputStream, null);
                } else {
                    onFlushSuccess(inputStream);
                }
            } catch (IOException e) {
                try {
                    onFlushError(connection.getResponseCode(), connection.getErrorStream(), e);
                } catch (IOException e1) {
                    IOUtils.consumeAndClose(connection.getErrorStream());
                    logger.warn(e.getMessage(), e);
                }
            } finally {
                connection.disconnect();
                connection = null;
                deflater.reset();
            }
        }
    }

    private void onFlushSuccess(InputStream inputStream) {
        reported += currentlyTransmitting;
        currentlyTransmitting = 0;
        // in order to be able to reuse the underlying TCP connections,
        // the input stream must be consumed and closed
        // see also https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html
        IOUtils.consumeAndClose(inputStream);
    }

    private void onFlushError(int responseCode, InputStream inputStream, @Nullable IOException e) {
        // TODO increment dropped count based on response
        logger.debug("APM server responded with {}", responseCode);
        if (e != null) {
            logger.warn(e.getMessage(), e);
        }
        // TODO proper error logging based on apm server response
        // in order to be able to reuse the underlying TCP connections,
        // the input stream must be consumed and closed
        // see also https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html
        IOUtils.consumeAndClose(inputStream);
    }

    @Override
    public long getReported() {
        return reported;
    }

    @Override
    public long getDropped() {
        return 0;
    }
}
