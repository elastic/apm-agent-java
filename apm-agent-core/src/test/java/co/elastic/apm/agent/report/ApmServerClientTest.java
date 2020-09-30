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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.objectpool.impl.BookkeeperObjectPool;
import co.elastic.apm.agent.util.Version;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.assertj.core.util.Lists;
import org.awaitility.core.ConditionFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
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

    @Before
    public void setUp() throws MalformedURLException {
        URL url1 = new URL("http", "localhost", apmServer1.port(), "/");
        URL url2 = new URL("http", "localhost", apmServer2.port(), "/");
        // APM server 6.x style
        apmServer1.stubFor(get(urlEqualTo("/")).willReturn(okForJson(Map.of("ok", Map.of("version", "6.7.0-SNAPSHOT")))));
        apmServer1.stubFor(get(urlEqualTo("/test")).willReturn(notFound()));
        apmServer1.stubFor(get(urlEqualTo("/not-found")).willReturn(notFound()));
        // APM server 7+ style
        apmServer2.stubFor(get(urlEqualTo("/")).willReturn(okForJson(Map.of("version", "7.3.0-RC1"))));
        apmServer2.stubFor(get(urlEqualTo("/test")).willReturn(ok("hello from server 2")));
        apmServer2.stubFor(get(urlEqualTo("/not-found")).willReturn(notFound()));

        config = SpyConfiguration.createSpyConfig();
        reporterConfiguration = config.getConfig(ReporterConfiguration.class);
        objectPoolFactory = new TestObjectPoolFactory();
        doReturn(List.of(url1, url2)).when(reporterConfiguration).getServerUrls();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .withObjectPoolFactory(objectPoolFactory)
            .buildAndStart();

        apmServerClient = tracer.getApmServerClient();
        apmServerClient.start(tracer.getConfig(ReporterConfiguration.class).getServerUrls());
    }

    @Test
    public void testClientMethodsWithEmptyUrls() throws IOException {
        // tests setting server_urls to an empty string in configuration
        apmServerClient.start(Lists.emptyList());
        awaitUpToOneSecond().untilAsserted(
            () -> assertThat(apmServerClient.getApmServerVersion(1, TimeUnit.SECONDS)).isEqualTo(Version.UNKNOWN_VERSION)
        );
        assertThat(apmServerClient.getCurrentUrl()).isNull();
        assertThat(apmServerClient.appendPathToCurrentUrl("/path")).isNull();
        assertThat(apmServerClient.startRequest("/whatever")).isNull();
    }

    @Test
    public void testDroppingAndRecyclingEventsWithEmptyUrls() {
        Reporter reporter = tracer.getReporter();
        long droppedStart = reporter.getDropped();

        // tests setting server_urls to an empty string in configuration
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
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/test")));
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
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/test")));
        assertThat(apmServerClient.getErrorCount()).isEqualTo(1);
    }

    @Test
    public void testRetryFailure() {
        assertThatThrownBy(() -> apmServerClient.execute("/not-found", URLConnection::getInputStream))
            .isInstanceOf(FileNotFoundException.class)
            .matches(t -> t.getSuppressed().length == 1, "should have a suppressed exception");
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/not-found")));
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/not-found")));
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
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/not-found")));
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
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/not-found")));
        assertThat(apmServerClient.getErrorCount()).isEqualTo(0);
    }

    @Test
    public void testSimulateConcurrentConnectionError() {
        apmServerClient.incrementAndGetErrorCount(0);
        apmServerClient.incrementAndGetErrorCount(0);
        assertThat(apmServerClient.getErrorCount()).isOne();
    }

    @Test
    public void testGetServerUrlsVerifyThatServerUrlsWillBeReloaded() throws IOException {
        URL tempUrl = new URL("http", "localhost", 9999, "");
        config.save("server_urls", tempUrl.toString(), SpyConfiguration.CONFIG_SOURCE_NAME);

        List<URL> updatedServerUrls = apmServerClient.getServerUrls();

        assertThat(updatedServerUrls).isEqualTo(List.of(tempUrl));
    }

    @Test
    public void testApmServerVersion() throws IOException {
        // we have one 6.7.0 and one 7.x server, we should take the minimum version which is 6.7.0
        assertThat(apmServerClient.isAtLeast(Version.of("6.7.0"))).isTrue();
        assertThat(apmServerClient.isAtLeast(Version.of("6.7.1"))).isFalse();

        apmServer1.stubFor(get(urlEqualTo("/"))
            .willReturn(okForJson(Map.of("version", "6.6.1"))));
        config.save("server_urls", new URL("http", "localhost", apmServer1.port(), "/").toString(), SpyConfiguration.CONFIG_SOURCE_NAME);
        assertThat(apmServerClient.isAtLeast(Version.of("6.7.0"))).isFalse();

    }

    @Test
    public void testWithEmptyServerUrlList() {
        ApmServerClient client = new ApmServerClient(reporterConfiguration);
        client.start(Collections.emptyList());
        Exception exception = null;
        try {
            client.execute("/irrelevant", connection -> null);
        } catch (Exception e) {
            exception = e;
        }
        assertThat(exception).isNull();
    }
}
