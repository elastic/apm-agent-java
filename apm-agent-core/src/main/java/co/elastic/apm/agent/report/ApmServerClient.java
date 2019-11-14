/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.util.Version;
import co.elastic.apm.agent.util.VersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load-balances traffic and handles fail-overs to multiple APM Servers.
 * <p>
 * In contrast to most load-balancing algorithms, this does not round-robin for every new request.
 * The reasoning is that we want to be able to reuse the TCP connections to the APM Server as much as possible so that
 * initiating a new request is as fast as possible.
 * That's why we only round-robin to the next APM Server URL in the event of a connection error.
 * </p>
 * <p>
 * To achieve load-balancing, we shuffle the list of provided APM Server URLs.
 * The more agents need to communicate with the same set of servers,
 * the more even the load distribution will be.
 * That's because of the random order the APM Servers will be in the shuffled list.
 * The assumption is that we only need to multiple APM Servers if lots of agents are in use and therefore one server does not scale anymore.
 * </p>
 */
public class ApmServerClient {

    private static final Logger logger = LoggerFactory.getLogger(ApmServerClient.class);
    private static final String USER_AGENT = "elasticapm-java/" + VersionUtils.getAgentVersion();
    private static final Version APM_SERVER_NON_STRING_LABEL_SUPPORT = new Version("6.7.0");
    private final ReporterConfiguration reporterConfiguration;
    private volatile List<URL> serverUrls;
    private volatile Future<Version> apmServerVersion;
    private final AtomicInteger errorCount = new AtomicInteger();
    private final ApmServerHealthChecker healthChecker;

    public ApmServerClient(ReporterConfiguration reporterConfiguration) {
        this(reporterConfiguration, shuffleUrls(reporterConfiguration.getServerUrls()));
    }

    public ApmServerClient(ReporterConfiguration reporterConfiguration, List<URL> shuffledUrls) {
        this.reporterConfiguration = reporterConfiguration;
        this.healthChecker = new ApmServerHealthChecker(this);
        this.reporterConfiguration.getServerUrlsOption().addChangeListener(new ConfigurationOption.ChangeListener<List<URL>>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, List<URL> oldValue, List<URL> newValue) {
                logger.debug("server_urls override with value = ({}).", newValue);
                if (newValue != null && !newValue.isEmpty()) {
                    setServerUrls(shuffleUrls(newValue));
                }
            }
        });
        setServerUrls(Collections.unmodifiableList(shuffledUrls));
    }

    private void setServerUrls(List<URL> serverUrls) {
        this.serverUrls = serverUrls;
        this.apmServerVersion = healthChecker.checkHealthAndGetMinVersion();
        this.errorCount.set(0);
    }

    private static List<URL> shuffleUrls(List<URL> serverUrls) {
        // shuffling the URL list helps to distribute the load across the apm servers
        // when there are multiple agents, they should not all start connecting to the same apm server
        List<URL> copy = new ArrayList<>(serverUrls);
        Collections.shuffle(copy);
        return copy;
    }

    private static void trustAll(HttpsURLConnection connection) {
        final SSLSocketFactory sf = SslUtils.getTrustAllSocketFactory();
        if (sf != null) {
            // using the same instances is important for TCP connection reuse
            connection.setHostnameVerifier(SslUtils.getTrustAllHostnameVerifyer());
            connection.setSSLSocketFactory(sf);
        }
    }

    HttpURLConnection startRequest(String relativePath) throws IOException {
        return startRequestToUrl(appendPathToCurrentUrl(relativePath));
    }

    @Nonnull
    private HttpURLConnection startRequestToUrl(URL url) throws IOException {
        final URLConnection connection = url.openConnection();
        if (!reporterConfiguration.isVerifyServerCert()) {
            if (connection instanceof HttpsURLConnection) {
                trustAll((HttpsURLConnection) connection);
            }
        }
        if (reporterConfiguration.getSecretToken() != null) {
            connection.setRequestProperty("Authorization", "Bearer " + reporterConfiguration.getSecretToken());
        }
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setConnectTimeout((int) reporterConfiguration.getServerTimeout().getMillis());
        connection.setReadTimeout((int) reporterConfiguration.getServerTimeout().getMillis());
        return (HttpURLConnection) connection;
    }

    @Nonnull
    URL appendPathToCurrentUrl(String apmServerPath) throws MalformedURLException {
        return appendPath(getCurrentUrl(), apmServerPath);
    }

    @Nonnull
    private URL appendPath(URL serverUrl, String apmServerPath) throws MalformedURLException {
        String path = serverUrl.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return new URL(serverUrl, path + apmServerPath);
    }

    /**
     * Instead of rotating the {@link #serverUrls} instance variable, this just increments an error counter which is read by
     * {@link #getCurrentUrl()} and {@link #getPrioritizedUrlList()} which rotate a copy of the immutable {@link #serverUrls} list.
     * This avoids that concurrently running requests influence each other.
     * <p>
     * This design is inspired by org.elasticsearch.client.RestClient
     * </p>
     * <p>
     * If the expected error count does not match the actual count,
     * the error count is not incremented.
     * This avoids concurrent requests from incrementing the error multiple times due to only one failing server.
     * </p>
     *
     * @param expectedErrorCount the error count that is expected by the current thread
     * @return the new expected error count
     */
    int incrementAndGetErrorCount(int expectedErrorCount) {
        boolean success = errorCount.compareAndSet(expectedErrorCount, expectedErrorCount + 1);
        if (success) {
            return expectedErrorCount + 1;
        } else {
            // this thread has a stale error count and may not increment the error count when another retry fails
            return -1;
        }
    }

    /**
     * Similar to {@link #incrementAndGetErrorCount(int)} but without guarding against concurrent connection errors.
     * This relieves the user from maintaining a separate error count.
     */
    void onConnectionError() {
        errorCount.incrementAndGet();
    }

    /**
     * Executes a request to the APM Server and returns the result from the provided {@link ConnectionHandler}.
     * If there's a connection error executing the request,
     * the request is retried with the next APM Server url.
     * The maximum amount of retries is the number of configured APM Server URLs.
     *
     * @param path              the APM Server path
     * @param connectionHandler receives the {@link HttpURLConnection} and returns the result
     * @param <V>               the result type
     * @return the result of the provided {@link Callable}
     * @throws Exception in case all retries yield an exception, the last will be thrown
     */
    @Nullable
    public <V> V execute(String path, ConnectionHandler<V> connectionHandler) throws Exception {
        final List<URL> prioritizedUrlList = getPrioritizedUrlList();
        if (prioritizedUrlList.isEmpty()) {
            return null;
        }

        int expectedErrorCount = errorCount.get();
        Exception previousException = null;
        for (URL serverUrl : prioritizedUrlList) {
            HttpURLConnection connection = null;
            try {
                connection = startRequestToUrl(appendPath(serverUrl, path));
                return connectionHandler.withConnection(connection);
            } catch (Exception e) {
                expectedErrorCount = incrementAndGetErrorCount(expectedErrorCount);
                logger.debug("Exception while interacting with APM Server, trying next one.");
                if (previousException != null) {
                    e.addSuppressed(previousException);
                }
                previousException = e;
            } finally {
                HttpUtils.consumeAndClose(connection);
            }
        }
        if (previousException == null) {
            throw new IllegalStateException("Expected previousException not to be null");
        }
        throw previousException;
    }

    public <T> List<T> executeForAllUrls(String path, ConnectionHandler<T> connectionHandler) {
        List<T> results = new ArrayList<>(serverUrls.size());
        for (URL serverUrl : serverUrls) {
            HttpURLConnection connection = null;
            try {
                connection = startRequestToUrl(appendPath(serverUrl, path));
                results.add(connectionHandler.withConnection(connection));
            } catch (Exception e) {
                logger.debug("Exception while interacting with APM Server", e);
            } finally {
                HttpUtils.consumeAndClose(connection);
            }
        }
        return results;
    }

    URL getCurrentUrl() {
        return serverUrls.get(errorCount.get() % serverUrls.size());
    }

    /**
     * Returns a copy of {@link #serverUrls} which contains the {@link #getCurrentUrl() current URL} as the first element
     *
     * @return a copy of {@link #serverUrls} which contains the {@link #getCurrentUrl() current URL} as the first element
     */
    @Nonnull
    private List<URL> getPrioritizedUrlList() {
        // Copying the URLs instead of rotating serverUrls makes sure that a concurrently happening connection error
        // for a different request does not skip a URL.
        // In other words, it avoids that concurrently running requests influence each other.
        ArrayList<URL> serverUrlsCopy = new ArrayList<>(serverUrls);
        Collections.rotate(serverUrlsCopy, errorCount.get());
        return serverUrlsCopy;
    }

    int getErrorCount() {
        return errorCount.get();
    }

    List<URL> getServerUrls() {
        return this.serverUrls;
    }

    public boolean supportsNonStringLabels() {
        return isAtLeast(APM_SERVER_NON_STRING_LABEL_SUPPORT);
    }

    public boolean isAtLeast(Version apmServerVersion) {
        try {
            Version localApmServerVersion = this.apmServerVersion.get();
            if (localApmServerVersion == null) {
                return false;
            }
            return localApmServerVersion.compareTo(apmServerVersion) >= 0;
        } catch (Exception e) {
            logger.debug(e.getMessage(), e);
            return false;
        }
    }

    public interface ConnectionHandler<T> {

        /**
         * Gets the {@link HttpURLConnection} after the connection has been established and returns a result,
         * for example the status code or the response body.
         *
         * NOTE: do not call {@link InputStream#close()} as that is handled by {@link ApmServerClient}
         *
         * @param connection the connection
         * @return the result
         * @throws IOException if an I/O error occurs while handling the connection
         */
        @Nullable
        T withConnection(HttpURLConnection connection) throws IOException;
    }

}
