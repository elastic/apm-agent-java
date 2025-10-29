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
package co.elastic.apm.agent.vertx.webclient;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public abstract class AbstractVertxWebClientTest extends AbstractHttpClientInstrumentationTest {

    private Vertx vertx;
    protected WebClient client;

    @Before
    public void setUp() {

        // This property is needed as otherwise Vert.x event loop threads won't have a context class loader (null)
        // which leads to NullPointerExceptions when spans are JSON validated in Unit tests
        System.setProperty("vertx.disableTCCL", "true");
        AbstractSpanImpl activeSpan = tracer.getActive();

        // deactivate current span to avoid tracing web client creation for JUnit test
        activeSpan.deactivate();
        vertx = Vertx.vertx();
        client = WebClient.create(vertx);
        activeSpan.activate();
    }

    @After
    public void tearDown() {
        try {
            close(vertx);
        } finally {
            closeWebClient(client);
        }
    }

    protected void closeWebClient(WebClient client) {
        client.close();
    }

    @Override
    protected void performGet(String path) throws Exception {
        VertxTestContext testContext = new VertxTestContext();
        get(client.getAbs(path), testContext);
        assertThat(testContext.awaitCompletion(1000, TimeUnit.SECONDS)).isTrue();
        if (testContext.failed()) {
            throw new Exception(testContext.causeOfFailure());
        }
    }

    @Test
    public void testFailedRequest() {
        try {
            performGet(String.format("http://not-existing.com:%s/error", wireMockRule.getPort()));
        } catch (Exception e) {
            // expected
        }

        ErrorCaptureImpl error = reporter.getFirstError(500);
        assertThat(error.getException()).isNotNull();
        assertThat(error.getException().getClass()).isNotNull();
        assertThat(error.getException().getMessage()).contains("not-existing.com");
        assertThat(error.getTraceContext().getTraceId()).isEqualTo(tracer.currentTransaction().getTraceContext().getTraceId());

        doVerifyFailedRequestHttpSpan("not-existing.com", "/error");
    }

    protected void doVerifyFailedRequestHttpSpan(String host, String path) {
        expectSpan(path)
            .withHost(host)
            .withStatus(0)
            .withoutRequestExecuted()
            .verify();

    }

    abstract protected void get(HttpRequest<Buffer> httpRequest, VertxTestContext testContext);

    abstract protected void close(Vertx vertx);

    @Override
    protected boolean isIpv6Supported() {
        return false;
    }

    @Override
    protected boolean isErrorOnCircularRedirectSupported() {
        return false;
    }
}
