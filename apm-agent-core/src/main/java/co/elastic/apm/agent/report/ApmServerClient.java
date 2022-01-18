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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.report.ssl.SslUtils;
import co.elastic.apm.agent.util.UrlConnectionUtils;
import co.elastic.apm.agent.util.Version;
import co.elastic.apm.agent.util.VersionUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    private static final Version VERSION_6_7 = Version.of("6.7.0");
    private static final Version VERSION_7_0 = Version.of("7.0.0");
    private static final Version VERSION_7_4 = Version.of("7.4.0");
    private static final Version VERSION_7_9 = Version.of("7.9.0");
    private static final Version VERSION_8_0 = Version.of("8.0.0");

    private final ReporterConfiguration reporterConfiguration;
    @Nullable
    private volatile List<URL> serverUrls;
    @Nullable
    private volatile Future<Version> apmServerVersion;
    private final AtomicInteger errorCount = new AtomicInteger();
    private final ApmServerHealthChecker healthChecker;

    private final String userAgent;

    public ApmServerClient(ReporterConfiguration reporterConfiguration, CoreConfiguration coreConfiguration) {
        this.reporterConfiguration = reporterConfiguration;
        this.healthChecker = new ApmServerHealthChecker(this);
        this.userAgent = getUserAgent(coreConfiguration);
    }

    public void start() {
        start(shuffleUrls(reporterConfiguration.getServerUrls()));
    }

    public void start(List<URL> shuffledUrls) {
        reporterConfiguration.getServerUrlOption().addChangeListener(new ConfigurationOption.ChangeListener<URL>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, URL oldValue, URL newValue) {
                logger.debug("server_url overridden with value = ({}).", newValue);
                setServerUrls(reporterConfiguration.getServerUrls());
            }
        });
        reporterConfiguration.getServerUrlsOption().addChangeListener(new ConfigurationOption.ChangeListener<List<URL>>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, List<URL> oldValue, List<URL> newValue) {
                logger.debug("server_urls overridden with value = ({}).", newValue);
                setServerUrls(reporterConfiguration.getServerUrls());
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

    @Nullable
    HttpURLConnection startRequest(String relativePath) throws IOException {
        URL url = appendPathToCurrentUrl(relativePath);
        if (url == null) {
            return null;
        }
        return startRequestToUrl(url);
    }

    @Nonnull
    private HttpURLConnection startRequestToUrl(URL url) throws IOException {
        final URLConnection connection = UrlConnectionUtils.openUrlConnectionThreadSafely(url);

        // change SSL socket factory to support both TLS fallback and disabling certificate validation
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
            boolean verifyServerCert = reporterConfiguration.isVerifyServerCert();

            if (!verifyServerCert) {
                httpsConnection.setHostnameVerifier(SslUtils.getTrustAllHostnameVerifier());
            }
            SSLSocketFactory sslSocketFactory = SslUtils.getSSLSocketFactory(verifyServerCert);
            if (sslSocketFactory != null) {
                httpsConnection.setSSLSocketFactory(sslSocketFactory);
            }
        }

        String secretToken = reporterConfiguration.getSecretToken();
        String apiKey = reporterConfiguration.getApiKey();
        String authHeaderValue = null;

        if (apiKey != null) {
            authHeaderValue = String.format("ApiKey %s", apiKey);
        } else if (secretToken != null) {
            authHeaderValue = String.format("Bearer %s", secretToken);
        }

        if (authHeaderValue != null) {
            connection.setRequestProperty("Authorization", authHeaderValue);
        }

        connection.setRequestProperty("User-Agent", userAgent);
        connection.setConnectTimeout((int) reporterConfiguration.getServerTimeout().getMillis());
        connection.setReadTimeout((int) reporterConfiguration.getServerTimeout().getMillis());
        return (HttpURLConnection) connection;
    }

    @Nullable
    URL appendPathToCurrentUrl(String apmServerPath) throws MalformedURLException {
        URL currentUrl = getCurrentUrl();
        if (currentUrl == null) {
            return null;
        }
        return appendPath(currentUrl, apmServerPath);
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
        List<URL> serverUrls = getServerUrls();
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

    @Nullable
    URL getCurrentUrl() {
        List<URL> serverUrls = getServerUrls();
        if (serverUrls.isEmpty()) {
            return null;
        }
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
        ArrayList<URL> serverUrlsCopy = new ArrayList<>(getServerUrls());
        Collections.rotate(serverUrlsCopy, errorCount.get());
        return serverUrlsCopy;
    }

    int getErrorCount() {
        return errorCount.get();
    }

    List<URL> getServerUrls() {
        if (serverUrls == null) {
            throw new IllegalStateException("APM Server client not yet initialized");
        }
        return serverUrls;
    }

    public boolean supportsNonStringLabels() {
        return isAtLeast(VERSION_6_7);
    }

    public boolean supportsNumericUrlPort() {
        return isAtLeast(VERSION_7_0);
    }

    public boolean supportsMultipleHeaderValues() {
        return isAtLeast(VERSION_7_0);
    }

    public boolean supportsConfiguredAndDetectedHostname() {
        return isAtLeast(VERSION_7_4);
    }

    public boolean supportsLogsEndpoint() {
        return isAtLeast(VERSION_7_9);
    }

    public boolean supportsKeepingUnsampledTransaction() {
        // supportsKeepingUnsampledTransaction is called from application threads
        // return true instead of blocking the thread
        if (apmServerVersion != null && !apmServerVersion.isDone()) {
            return true;
        }
        return isLowerThan(VERSION_8_0);
    }

    @Nullable
    Version getApmServerVersion(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
        if (apmServerVersion != null) {
            return apmServerVersion.get(timeout, timeUnit);
        }
        return null;
    }

    public boolean isLowerThan(Version apmServerVersion) {
        return !isAtLeast(apmServerVersion);
    }

    public boolean isAtLeast(Version apmServerVersion) {
        if (this.apmServerVersion == null) {
            throw new IllegalStateException("Called before init event");
        }
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
         * <p>
         * NOTE: do not call {@link InputStream#close()} as that is handled by {@link ApmServerClient}
         *
         * @param connection the connection
         * @return the result
         * @throws IOException if an I/O error occurs while handling the connection
         */
        @Nullable
        T withConnection(HttpURLConnection connection) throws IOException;
    }

    private static String getUserAgent(CoreConfiguration coreConfiguration) {
        StringBuilder userAgent = new StringBuilder();
        userAgent.append("apm-agent-java/").append(VersionUtils.getAgentVersion());
        String serviceName = coreConfiguration.getServiceName();
        String serviceVersion = coreConfiguration.getServiceVersion();
        if (!serviceName.isEmpty()) {
            userAgent.append(" (").append(serviceName);
            if (serviceVersion != null && !serviceVersion.isEmpty()) {
                userAgent.append(" ").append(escapeHeaderComment(serviceVersion));
            }
            userAgent.append(")");
        }
        return userAgent.toString();
    }

    /**
     * Escapes the provided string from characters that are disallowed within HTTP header comments.
     * See spec- https://httpwg.org/specs/rfc7230.html#field.components
     * @param headerFieldComment HTTP header comment value to be escaped
     * @return the escaped header comment
     */
    static String escapeHeaderComment(String headerFieldComment) {
        return headerFieldComment.replaceAll("[^\\t \\x21-\\x27\\x2a-\\x5b\\x5d-\\x7e\\x80-\\xff]", "_");
    }
}
