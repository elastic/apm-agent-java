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

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.source.ConfigSources;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.objectpool.impl.BookkeeperObjectPool;
import co.elastic.apm.agent.common.util.Version;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.assertj.core.util.Lists;
import org.awaitility.core.ConditionFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.converter.UrlValueConverter;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;

public class ApmServerClientTest {

    @Rule
    public WireMockRule apmServer1 = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());
    @Rule
    public WireMockRule apmServer2 = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());
    private ApmServerClient apmServerClient;
    private ConfigurationRegistry config;
    private ElasticApmTracer tracer;
    private TestObjectPoolFactory objectPoolFactory;
    private ReporterConfiguration reporterConfiguration;
    private CoreConfiguration coreConfiguration;
    private List<URL> urlList;

    @Before
    public void setUp() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        URL url1 = new URL("http", "localhost", apmServer1.port(), "/");
        URL url2 = new URL("http", "localhost", apmServer2.port(), "/proxy");
        // APM server 6.x style
        apmServer1.stubFor(get(urlEqualTo("/")).willReturn(okForJson(Map.of("ok", Map.of("version", "6.7.1-SNAPSHOT")))));

        apmServer1.stubFor(get(urlEqualTo("/test")).willReturn(notFound()));
        apmServer1.stubFor(get(urlEqualTo("/not-found")).willReturn(notFound()));
        // APM server 7+ style
        apmServer2.stubFor(get(urlEqualTo("/proxy/")).willReturn(okForJson(Map.of("version", "7.3.0-RC1"))));
        apmServer2.stubFor(get(urlEqualTo("/proxy/test")).willReturn(ok("hello from server 2")));
        apmServer2.stubFor(get(urlEqualTo("/proxy/not-found")).willReturn(notFound()));

        config = SpyConfiguration.createSpyConfig();
        reporterConfiguration = config.getConfig(ReporterConfiguration.class);
        coreConfiguration = config.getConfig(CoreConfiguration.class);
        objectPoolFactory = new TestObjectPoolFactory();
        config.save("server_urls", url1 + "," + url2, SpyConfiguration.CONFIG_SOURCE_NAME);
        urlList = List.of(UrlValueConverter.INSTANCE.convert(url1.toString()), UrlValueConverter.INSTANCE.convert(url2.toString()));

        apmServerClient = new ApmServerClient(reporterConfiguration, coreConfiguration);

        tracer = new ElasticApmTracerBuilder()
            .withApmServerClient(apmServerClient)
            .configurationRegistry(config)
            .withObjectPoolFactory(objectPoolFactory)
            .buildAndStart();

        // force a known order, with server1, then server2
        // tracer start will actually randomize it
        apmServerClient.start(urlList);

        //wait until the health request completes to prevent mockito race conditions
        apmServerClient.getApmServerVersion(10, TimeUnit.SECONDS);
    }

    @Test
    public void testClientMethodsWithEmptyUrls() throws IOException {
        // tests setting server_url to an empty string in configuration
        apmServerClient.start(Lists.emptyList());
        awaitUpToOneSecond().untilAsserted(
            () -> assertThat(apmServerClient.getApmServerVersion(1, TimeUnit.SECONDS)).isEqualTo(ApmServerHealthChecker.UNKNOWN_VERSION)
        );
        assertThat(apmServerClient.getCurrentUrl()).isNull();
        assertThat(apmServerClient.appendPathToCurrentUrl("/path")).isNull();
        assertThat(apmServerClient.startRequest("/whatever")).isNull();
    }

    @Test
    public void testDroppingAndRecyclingEventsWithEmptyUrls() {
        Reporter reporter = tracer.getReporter();
        long droppedStart = reporter.getDropped();

        // tests setting server_url to an empty string in configuration
        apmServerClient.start(Lists.emptyList());

        BookkeeperObjectPool<Transaction> transactionPool = objectPoolFactory.getTransactionPool();
        int transactionsRequestedBefore = transactionPool.getRequestedObjectCount();
        final Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader());
        assertThat(transactionPool.getRequestedObjectCount()).isEqualTo(transactionsRequestedBefore + 1);
        int transactionsInPoolAfterCreation = transactionPool.getObjectsInPool();

        BookkeeperObjectPool<Span> spanPool = objectPoolFactory.getSpanPool();
        int spansRequestedBefore = spanPool.getRequestedObjectCount();
        final Span span = Objects.requireNonNull(transaction).createSpan();
        assertThat(transactionPool.getRequestedObjectCount()).isEqualTo(spansRequestedBefore + 1);
        int spansInPoolAfterCreation = spanPool.getObjectsInPool();

        BookkeeperObjectPool<ErrorCapture> errorPool = objectPoolFactory.getErrorPool();
        int errorsRequestedBefore = errorPool.getRequestedObjectCount();
        span.captureException(new Throwable());
        assertThat(errorPool.getRequestedObjectCount()).isEqualTo(errorsRequestedBefore + 1);
        int errorsInPoolAfterCreation = errorPool.getObjectsInPool();

        span.end();
        transaction.end();

        awaitUpToOneSecond().untilAsserted(
            () -> assertThat(transactionPool.getObjectsInPool()).isEqualTo(transactionsInPoolAfterCreation + 1)
        );
        assertThat(spanPool.getObjectsInPool()).isEqualTo(spansInPoolAfterCreation + 1);
        assertThat(errorPool.getObjectsInPool()).isEqualTo(errorsInPoolAfterCreation + 1);
        assertThat(reporter.getDropped()).isEqualTo(droppedStart + 3);
    }

    private static ConditionFactory awaitUpToOneSecond() {
        return await()
            .pollInterval(1, TimeUnit.MILLISECONDS)
            .timeout(1000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testInitialCurrentUrlIsFirstUrl() throws Exception {
        assertThat(Objects.requireNonNull(apmServerClient.getCurrentUrl()).getPort()).isEqualTo(apmServer1.port());

        apmServerClient.execute("/test", HttpURLConnection::getResponseCode);

        apmServer1.verify(1, getRequestedFor(urlEqualTo("/test")));
        apmServer2.verify(0, getRequestedFor(urlEqualTo("/test")));
    }

    @Test
    public void testUseNextUrlOnError() throws Exception {
        apmServerClient.incrementAndGetErrorCount(0);
        assertThat(Objects.requireNonNull(apmServerClient.getCurrentUrl()).getPort()).isEqualTo(apmServer2.port());

        apmServerClient.execute("/test", HttpURLConnection::getResponseCode);

        apmServer1.verify(0, getRequestedFor(urlEqualTo("/test")));
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/proxy/test")));
        assertThat(apmServerClient.getErrorCount()).isEqualTo(1);
    }

    @Test
    public void testWrapUrlsOnConsecutiveError() throws Exception {
        int expectedErrorCount = apmServerClient.incrementAndGetErrorCount(0);
        apmServerClient.incrementAndGetErrorCount(expectedErrorCount);

        testInitialCurrentUrlIsFirstUrl();
        assertThat(apmServerClient.getErrorCount()).isEqualTo(2);
    }

    @Test
    public void testRetry() throws Exception {
        assertThat(apmServerClient.<String>execute("/test", conn -> new String(conn.getInputStream().readAllBytes()))).isEqualTo("hello from server 2");
        assertThat(Objects.requireNonNull(apmServerClient.getCurrentUrl()).getPort()).isEqualTo(apmServer2.port());
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/test")));
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/proxy/test")));
        assertThat(apmServerClient.getErrorCount()).isEqualTo(1);
    }

    @Test
    public void testRetryFailure() {
        assertThatThrownBy(() -> apmServerClient.execute("/not-found", URLConnection::getInputStream))
            .isInstanceOf(FileNotFoundException.class)
            .matches(t -> t.getSuppressed().length == 1, "should have a suppressed exception");
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/not-found")));
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/proxy/not-found")));
        // two failures -> urls wrap
        assertThat(Objects.requireNonNull(apmServerClient.getCurrentUrl()).getPort()).isEqualTo(apmServer1.port());
        assertThat(apmServerClient.getErrorCount()).isEqualTo(2);
    }

    @Test
    public void testExecuteSuccessfullyForAllUrls() {
        apmServerClient.executeForAllUrls("/not-found", connection -> {
            connection.getResponseCode();
            return null;
        });
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/not-found")));
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/proxy/not-found")));
        // no failures -> urls in initial state
        assertThat(Objects.requireNonNull(apmServerClient.getCurrentUrl()).getPort()).isEqualTo(apmServer1.port());
        assertThat(apmServerClient.getErrorCount()).isZero();
    }

    @Test
    public void testExecuteFailureForAllUrls() {
        // exception will only be logged, not thrown
        apmServerClient.executeForAllUrls("/not-found", connection -> {
            connection.getInputStream();
            return null;
        });
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/not-found")));
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/proxy/not-found")));
        assertThat(apmServerClient.getErrorCount()).isEqualTo(0);
    }

    @Test
    public void testSimulateConcurrentConnectionError() {
        apmServerClient.incrementAndGetErrorCount(0);
        apmServerClient.incrementAndGetErrorCount(0);
        assertThat(apmServerClient.getErrorCount()).isOne();
    }

    @Test
    public void testServerUrlsOverridesDefaultServerUrl() {
        List<URL> updatedServerUrls = apmServerClient.getServerUrls();
        // since only server_urls is set, we expect it to override the default server_url setting
        assertThat(updatedServerUrls).isEqualTo(urlList);
    }

    @Test
    public void testServerUrlsIsReloadedOnChange() throws IOException {
        config.save("server_urls", "http://localhost:9999,http://localhost:9998", SpyConfiguration.CONFIG_SOURCE_NAME);
        List<URL> updatedServerUrls = apmServerClient.getServerUrls();
        assertThat(updatedServerUrls).isEqualTo(List.of(
            UrlValueConverter.INSTANCE.convert("http://localhost:9999"),
            UrlValueConverter.INSTANCE.convert("http://localhost:9998")
        ));
    }

    @Test
    public void testDefaultServerUrls() throws IOException {
        config.save("server_urls", "", SpyConfiguration.CONFIG_SOURCE_NAME);
        List<URL> updatedServerUrls = apmServerClient.getServerUrls();
        assertThat(updatedServerUrls).hasSize(1);
        URL defaultServerUrl = updatedServerUrls.get(0);
        assertThat(defaultServerUrl.getHost()).isNotEqualTo("localhost");
        assertThat(defaultServerUrl.getHost()).isEqualTo("127.0.0.1");
    }

    @Test
    public void testServerUrlSettingOverridesServerUrls() throws IOException {
        URL tempUrl = new URL("http", "localhost", 9999, "");
        config.save("server_url", tempUrl.toString(), SpyConfiguration.CONFIG_SOURCE_NAME);
        List<URL> updatedServerUrls = apmServerClient.getServerUrls();
        assertThat(updatedServerUrls).isEqualTo(List.of(tempUrl));
    }

    @Test
    public void testDisableSend() {
        // We have to go through that because the disable_send config is non-dynamic
        ConfigurationRegistry localConfig = SpyConfiguration.createSpyConfig(
            Objects.requireNonNull(ConfigSources.fromClasspath("test.elasticapm.disable-send.properties", ClassLoader.getSystemClassLoader()))
        );
        final ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .reporter(new MockReporter())
            .configurationRegistry(localConfig)
            .buildAndStart();
        List<URL> updatedServerUrls = tracer.getApmServerClient().getServerUrls();
        assertThat(updatedServerUrls).isEmpty();
    }

    @Test
    public void testApmServerVersion() {
        assertThat(apmServerClient.isAtLeast(Version.of("6.7.0"))).isTrue();
        assertThat(apmServerClient.isAtLeast(Version.of("6.7.1"))).isFalse();
        assertThat(apmServerClient.supportsNonStringLabels()).isTrue();

        stubServerVersion(apmServer1, "6.6.1");
        checkApmServerVersion("6.6.1");
        assertThat(apmServerClient.supportsNonStringLabels()).isFalse();

    }

    @Test
    public void testWithEmptyServerUrlList() {
        ApmServerClient client = new ApmServerClient(reporterConfiguration, coreConfiguration);
        client.start(Collections.emptyList());
        Exception exception = null;
        try {
            client.execute("/irrelevant", connection -> null);
        } catch (Exception e) {
            exception = e;
        }
        assertThat(exception).isNull();
    }

    @Test
    public void testUserAgentHeaderEscaping() {
        assertThat(ApmServerClient.escapeHeaderComment("8()9")).isEqualTo("8__9");
        assertThat(ApmServerClient.escapeHeaderComment("iPad; U; CPU OS 3_2_1 like Mac OS X; en-us")).isEqualTo("iPad; U; CPU OS 3_2_1 like Mac OS X; en-us");
        assertThat(ApmServerClient.escapeHeaderComment("iPad; U; CPU \\OS 3_2_1 like Mac OS X; en-us")).isEqualTo("iPad; U; CPU _OS 3_2_1 like Mac OS X; en-us");
    }

    @Test
    public void testSupportUnsampledTransactions() {
        testSupportUnsampledTransactions(null, true);
        testSupportUnsampledTransactions("7.0.0", true);
        testSupportUnsampledTransactions("8.0.0", false);
    }

    @Test
    public void testApiKeyRotation() throws Exception {
        doReturn("token1").when(reporterConfiguration).getApiKey();

        apmServerClient.execute("/test", HttpURLConnection::getResponseCode);
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/test"))
            .withHeader("Authorization", equalTo("ApiKey token1")));

        doReturn("token2").when(reporterConfiguration).getApiKey();

        apmServerClient.execute("/test", HttpURLConnection::getResponseCode);
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/test"))
            .withHeader("Authorization", equalTo("ApiKey token2")));
    }


    @Test
    public void testSecretTokenRotation() throws Exception {
        doReturn("token1").when(reporterConfiguration).getSecretToken();

        apmServerClient.execute("/test", HttpURLConnection::getResponseCode);
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/test"))
            .withHeader("Authorization", equalTo("Bearer token1")));

        doReturn("token2").when(reporterConfiguration).getSecretToken();

        apmServerClient.execute("/test", HttpURLConnection::getResponseCode);
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/test"))
            .withHeader("Authorization", equalTo("Bearer token2")));
    }

    private void testSupportUnsampledTransactions(@Nullable String version, boolean expected) {
        stubServerVersion(version);
        assertThat(apmServerClient.supportsKeepingUnsampledTransaction())
            .describedAs("keeping unsampled transactions for version %s is expected to be %s", version, expected)
            .isEqualTo(expected);
    }

    private void stubServerVersion(@Nullable String version){
        // supported by default as we stub 6.x server by default
        if (version != null && !version.isEmpty()) {
            stubServerVersion(apmServer1, version);
        }

        // we have to re-create client as version is cached
        apmServerClient = new ApmServerClient(reporterConfiguration, coreConfiguration);
        apmServerClient.start(Collections.singletonList(UrlValueConverter.INSTANCE.convert(String.format("http://localhost:%d/", apmServer1.port()))));

        if (version != null) {
            // we have to check version to ensure it's not in-progress
            checkApmServerVersion(version);
        }
    }

    @Test
    public void testSupportLogsEnpoint() {
        String feature = "logs endpoint";
        Callable<Boolean> featureMethod = () -> apmServerClient.supportsLogsEndpoint();

        testSupportedFeature(feature, featureMethod,null, false);
        testSupportedFeature(feature, featureMethod,"8.5.99", false);
        testSupportedFeature(feature, featureMethod,"8.6.0", true);
        testSupportedFeature(feature, featureMethod,"9.0.0", true);
    }

    @Test
    public void testSupportsActivationMethod() {
        String feature = "agent activation method";
        Callable<Boolean> featureMethod = () -> apmServerClient.supportsActivationMethod();

        testSupportedFeature(feature, featureMethod, null, true);
        testSupportedFeature(feature, featureMethod, "8.6.99", false);
        testSupportedFeature(feature, featureMethod, "8.7.0", false);
        testSupportedFeature(feature, featureMethod, "8.7.1", true);
        testSupportedFeature(feature, featureMethod, "9.0.0", true);
    }

    @Test
    public void testSupportsSendingUnsampledTransactions() {
        String feature = "keep unsampled transactions";
        Callable<Boolean> featureMethod = () -> apmServerClient.supportsKeepingUnsampledTransaction();

        testSupportedFeature(feature, featureMethod, null, true);
        testSupportedFeature(feature, featureMethod, "7.99.99", true);
        testSupportedFeature(feature, featureMethod, "8.0.0", false);
        testSupportedFeature(feature, featureMethod, "9.0.0", false);
    }

    private void testSupportedFeature(String feature, Callable<Boolean> featureMethod, @Nullable String version, boolean expected) {
        stubServerVersion(version);
        Boolean result;
        try {
            result = featureMethod.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(result)
            .describedAs("%s for version '%s' is expected to be %s", feature, version, expected ? "supported" : "not supported")
            .isEqualTo(expected);
    }


    /**
     * Stubs the APM server endpoint with a specific version
     *
     * @param apmServer APM server wiremock rule
     * @param version   version to stub
     */
    void stubServerVersion(WireMockRule apmServer, String version) {
        apmServer.stubFor(get(urlEqualTo("/"))
            .willReturn(okForJson(Map.of("version", version))));

        try {
            URL url = new URL("http", "localhost", apmServer.port(), "/");
            config.save("server_url", url.toString(), SpyConfiguration.CONFIG_SOURCE_NAME);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

    }

    private void checkApmServerVersion(String expectedVersion) {
        // retrieving version is asynchronous, and implementation assumes default when the version hasn't been
        // actually retrieved from server
        Version serverVersion;
        try {
            serverVersion = apmServerClient.getApmServerVersion(1L, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        assertThat(serverVersion).isNotNull();
        assertThat(serverVersion.toString()).isEqualTo(expectedVersion);
    }
}
