/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * This reporter supports the nd-json HTTP streaming based intake v2 protocol
 */
public class IntakeV2ReportingEventHandler implements ReportingEventHandler {

    public static final String INTAKE_V2_URL = "/intake/v2/events";
    private static final Logger logger = LoggerFactory.getLogger(IntakeV2ReportingEventHandler.class);
    private static final int GZIP_COMPRESSION_LEVEL = 1;
    public static final String USER_AGENT = "java-agent/" + VersionUtils.getAgentVersion();

    private final ReporterConfiguration reporterConfiguration;
    private final ProcessorEventHandler processorEventHandler;
    private final MetaData metaData;
    private final PayloadSerializer payloadSerializer;
    private final Timer timeoutTimer;
    private final CyclicIterator<URL> serverUrlIterator;
    private Deflater deflater;
    private long currentlyTransmitting = 0;
    private long reported = 0;
    private long dropped = 0;
    @Nullable
    private HttpURLConnection connection;
    @Nullable
    private OutputStream os;
    @Nullable
    private ApmServerReporter reporter;
    @Nullable
    private TimerTask timeoutTask;
    private int errorCount;
    private long gracePeriodEnd;

    public IntakeV2ReportingEventHandler(Service service, ProcessInfo process, SystemInfo system,
                                         ReporterConfiguration reporterConfiguration, ProcessorEventHandler processorEventHandler,
                                         PayloadSerializer payloadSerializer) {
        this(service, process, system, reporterConfiguration, processorEventHandler, payloadSerializer, shuffleUrls(reporterConfiguration));
    }

    IntakeV2ReportingEventHandler(Service service, ProcessInfo process, SystemInfo system,
                                  ReporterConfiguration reporterConfiguration, ProcessorEventHandler processorEventHandler,
                                  PayloadSerializer payloadSerializer, List<URL> serverUrls) {
        this.reporterConfiguration = reporterConfiguration;
        this.processorEventHandler = processorEventHandler;
        this.payloadSerializer = payloadSerializer;
        this.metaData = new MetaData(process, service, system);
        this.deflater = new Deflater(GZIP_COMPRESSION_LEVEL);
        this.timeoutTimer = new Timer("apm-request-timeout-timer", true);
        this.serverUrlIterator = new CyclicIterator<>(serverUrls);
    }

    private static List<URL> shuffleUrls(ReporterConfiguration reporterConfiguration) {
        List<URL> serverUrls = new ArrayList<>(reporterConfiguration.getServerUrls());
        // shuffling the URL list helps to distribute the load across the apm servers
        // when there are multiple agents, they should not all start connecting to the same apm server
        Collections.shuffle(serverUrls);
        return serverUrls;
    }

    private static long calculateEndOfGracePeriod(long errorCount) {
        long backoffTimeSeconds = getBackoffTimeSeconds(errorCount);
        logger.info("Backing off for {} seconds (±10%)", backoffTimeSeconds);
        final long backoffTimeMillis = TimeUnit.SECONDS.toMillis(backoffTimeSeconds);
        return System.currentTimeMillis() + backoffTimeMillis + getRandomJitter(backoffTimeMillis);
    }

    /*
     * We add ±10% jitter to the calculated grace period in case multiple agents entered the grace period simultaneously.
     * This can happen if the APM server queue is full which leads to sending an error response to all connected agents.
     * The random jitter makes sure the agents will not all try to reconnect at the same time,
     * which would overwhelm the APM server again.
     */
    static long getRandomJitter(long backoffTimeMillis) {
        final long tenPercentOfBackoffTimeMillis = (long) (backoffTimeMillis * 0.1);
        return (long) (tenPercentOfBackoffTimeMillis * 2 * Math.random()) - tenPercentOfBackoffTimeMillis;
    }

    static long getBackoffTimeSeconds(long errorCount) {
        return (long) Math.pow(Math.min(errorCount, 6), 2);
    }

    @Override
    public void init(ApmServerReporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public void onEvent(ReportingEvent event, long sequence, boolean endOfBatch) {
        if (logger.isDebugEnabled()) {
            logger.debug("Receiving {} event (sequence {})", event.getType(), sequence);
        }
        try {
            handleEvent(event, sequence, endOfBatch);
        } finally {
            event.resetState();
        }
    }

    private void handleEvent(ReportingEvent event, long sequence, boolean endOfBatch) {

        if (event.getType() == null) {
            return;
        } else if (event.getType() == ReportingEvent.ReportingEventType.FLUSH) {
            flush();
            return;
        }
        processorEventHandler.onEvent(event, sequence, endOfBatch);
        if (connection == null) {
            if (gracePeriodEnd > System.currentTimeMillis()) {
                // back off because there are connection issues with the apm server
                dropped++;
                return;
            }
            connection = startRequest();
            payloadSerializer.serializeMetaDataNdJson(metaData);
        }
        try {
            writeEvent(event);
        } catch (Exception e) {
            // end request on error to start backing off
            flush();
            onConnectionError(null, currentlyTransmitting, 0);
        }
        if (shouldFlush()) {
            flush();
        }
    }

    private void writeEvent(ReportingEvent event) {
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
    }

    private boolean shouldFlush() {
        final long written = deflater.getBytesWritten() + DslJsonSerializer.BUFFER_SIZE;
        final boolean flush = written >= reporterConfiguration.getApiRequestSize();
        if (flush && logger.isDebugEnabled()) {
            logger.debug("Flushing, because request size limit exceeded {}/{}", written, reporterConfiguration.getApiRequestSize());
        }
        return flush;
    }

    @Nullable
    private HttpURLConnection startRequest() {
        try {
            URL url = getUrl();
            if (logger.isDebugEnabled()) {
                logger.debug("Starting new request to {}", url);
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (!reporterConfiguration.isVerifyServerCert()) {
                if (connection instanceof HttpsURLConnection) {
                    trustAll((HttpsURLConnection) connection);
                }
            }
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            if (reporterConfiguration.getSecretToken() != null) {
                connection.setRequestProperty("Authorization", "Bearer " + reporterConfiguration.getSecretToken());
            }
            connection.setChunkedStreamingMode(DslJsonSerializer.BUFFER_SIZE);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Content-Encoding", "deflate");
            connection.setRequestProperty("Content-Type", "application/x-ndjson");
            connection.setUseCaches(false);
            connection.setConnectTimeout((int) reporterConfiguration.getServerTimeout().getMillis());
            connection.setReadTimeout((int) reporterConfiguration.getServerTimeout().getMillis());
            connection.connect();
            os = new DeflaterOutputStream(connection.getOutputStream(), deflater);
            payloadSerializer.setOutputStream(os);
            if (reporter != null) {
                timeoutTask = new FlushOnTimeoutTimerTask(reporter);
                if (logger.isDebugEnabled()) {
                    logger.debug("Scheduling request timeout in {}", reporterConfiguration.getApiRequestTime());
                }
                timeoutTimer.schedule(timeoutTask, reporterConfiguration.getApiRequestTime().getMillis());
            }
            return connection;
        } catch (IOException e) {
            onConnectionError(null, currentlyTransmitting, 0);
            return null;
        }
    }

    @Nonnull
    URL getUrl() throws MalformedURLException {
        URL serverUrl = serverUrlIterator.get();
        String path = serverUrl.getPath();
        if(path.endsWith("/")) {
            path = path.substring(0, path.length()-1);
        }
        return new URL(serverUrl, path + INTAKE_V2_URL);
    }

    void switchToNextServerUrl() {
        serverUrlIterator.next();
    }

    private void trustAll(HttpsURLConnection connection) {
        final SSLSocketFactory sf = SslUtils.getTrustAllSocketFactory();
        if (sf != null) {
            // using the same instances is important for TCP connection reuse
            connection.setHostnameVerifier(SslUtils.getTrustAllHostnameVerifyer());
            connection.setSSLSocketFactory(sf);
        }
    }

    void flush() {
        cancelTimeout();
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
                    onFlushError(-1, connection.getErrorStream(), e);
                }
            } finally {
                connection.disconnect();
                connection = null;
                deflater.reset();
                currentlyTransmitting = 0;
            }
        }
    }

    private void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    private void onFlushSuccess(InputStream inputStream) {
        errorCount = 0;
        reported += currentlyTransmitting;
        // in order to be able to reuse the underlying TCP connections,
        // the input stream must be consumed and closed
        // see also https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html
        IOUtils.consumeAndClose(inputStream);
    }

    private void onFlushError(Integer responseCode, InputStream inputStream, @Nullable IOException e) {
        // TODO read accepted, dropped and invalid
        onConnectionError(responseCode, currentlyTransmitting, 0);
        if (e != null) {
            logger.warn(e.getMessage());
            logger.debug("Sending payload to APM server failed with {}", responseCode, e);
        }
        if (logger.isWarnEnabled()) {
            try {
                logger.warn(IOUtils.toString(inputStream));
            } catch (IOException e1) {
                logger.warn(e1.getMessage(), e);
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        } else {
            // in order to be able to reuse the underlying TCP connections,
            // the input stream must be consumed and closed
            // see also https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html
            IOUtils.consumeAndClose(inputStream);
        }
    }

    private void onConnectionError(@Nullable Integer responseCode, long droppedEvents, long reportedEvents) {
        gracePeriodEnd = calculateEndOfGracePeriod(errorCount++);
        dropped += droppedEvents;
        reported += reportedEvents;
        // if the response code is null, the server did not even send a response
        if (responseCode == null || responseCode > 429) {
            // this server seems to have connection or capacity issues, try next
            switchToNextServerUrl();
        } else if (responseCode == 404) {
            logger.warn("It seems like you are using a version of the APM Server which is not compatible with this agent. " +
                "Please use APM Server 6.5.0 or newer.");
        }
    }

    @Override
    public long getReported() {
        return reported;
    }

    @Override
    public long getDropped() {
        return dropped;
    }

    @Override
    public void close() {
        timeoutTimer.cancel();
    }

    private static class FlushOnTimeoutTimerTask extends TimerTask {
        private final ApmServerReporter reporter;
        @Nullable
        private volatile Future<Void> flush;

        private FlushOnTimeoutTimerTask(ApmServerReporter reporter) {
            this.reporter = reporter;
        }

        @Override
        public void run() {
            // if the ring buffer is full this waits until a slot becomes available
            // as this happens on a different thread,
            // the reporting does not block and thus there is no danger of deadlocks
            logger.debug("Request flush because the request timeout occurred");
            flush = reporter.flush();
        }

        @Override
        public boolean cancel() {
            final boolean cancel = super.cancel();
            final Future<Void> flush = this.flush;
            if (flush != null) {
                flush.cancel(false);
            }
            return cancel;
        }
    }

    private static class CyclicIterator<T> {
        private final Iterable<T> iterable;
        private Iterator<T> iterator;
        private T current;

        public CyclicIterator(Iterable<T> iterable) {
            this.iterable = iterable;
            iterator = this.iterable.iterator();
            current = iterator.next();
        }

        public T get() {
            return current;
        }

        public void next() {
            if (!iterator.hasNext()) {
                iterator = iterable.iterator();
            }
            current = iterator.next();
        }
    }
}
