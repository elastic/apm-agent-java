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
package co.elastic.apm.agent.ratpack;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.web.WebConfiguration;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ratpack.exec.Blocking;
import ratpack.exec.Promise;
import ratpack.server.RatpackServer;
import ratpack.test.ServerBackedApplicationUnderTest;
import ratpack.test.http.TestHttpClient;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class RatpackHandlerInstrumentationTest extends AbstractInstrumentationTest {

    @Nullable
    private static ServerBackedApplicationUnderTest applicationUnderTest;
    @Nullable
    private WebConfiguration webConfiguration;
    @Nullable
    private CoreConfiguration coreConfiguration;
    @Nullable
    private TestHttpClient client;

    @BeforeAll
    static void createServer() throws Exception {
        applicationUnderTest = ServerBackedApplicationUnderTest.of(
            RatpackServer.of(s -> {
                s.serverConfig(c ->
                    c.port(0));
                s.handlers(c -> {

                    // These are happy path tests. The Blocking calls ensure that multiple execution segments are created
                    // on both thread pools
                    c.get("happy/:iteration", ctx -> {

                        final String iteration = ctx.getPathTokens().getOrDefault("iteration", "-1");

                        Blocking.get(() ->
                            Blocking.on(Promise.value("Hello World! (" + iteration + ")"))
                        ).then(ctx::render);
                    });

                    // Handler to make sure that a transaction captures an exception
                    c.get("fail/:iteration", ctx -> Blocking.get(() -> {
                        throw new UnsupportedOperationException("failed while blocking");
                    }).then(ctx::render));

                    // Handler to see if the body of a request can be captured.
                    c.post("capture-body", ctx -> {

                        // echo
                        ctx.getRequest().getBody().then(typedData ->
                            ctx.render(typedData.getText()));
                    });

                    // Handler to see if the body of a request can be captured (streaming)
                    c.post("capture-stream", ctx -> {

                        // echo
                        ctx.getRequest().getBodyStream().toPromise()
                            .then(buf ->
                                ctx.getResponse().send(buf));
                    });
                });
            })
        );
    }

    @AfterAll
    static void closeServer() {
        assert applicationUnderTest != null;
        applicationUnderTest.close();
    }

    @BeforeEach
    final void setUp() throws IOException {

        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        coreConfiguration.getSampleRate().update(1d, SpyConfiguration.CONFIG_SOURCE_NAME);

        webConfiguration = tracer.getConfig(WebConfiguration.class);
        when(webConfiguration.getCaptureBody()).thenReturn(WebConfiguration.EventType.ALL);

        assert applicationUnderTest != null;

        client = applicationUnderTest.getHttpClient();
    }

    // Repetition count should be greater than 2 * available processors (size of compute thread pool)
    @RepeatedTest(30)
    void shouldInstrumentRequestWithTransaction(RepetitionInfo repetitionInfo) throws Exception{
        assert client != null;

        // given
        final int i = repetitionInfo.getCurrentRepetition();
        final String path = "/happy/" + i;

        // when
        client.get(path);

        // then
        assertHasOneTransaction("GET /happy/:iteration", 200);
        assertFirstRequestContains(path, Objects::isNull);
    }

    // Repetition count should be greater than 2 * available processors (size of compute thread pool)
    @RepeatedTest(30)
    void shouldReportErrorsInTransaction(RepetitionInfo repetitionInfo) throws Exception {
        assert client != null;

        // given
        final int i = repetitionInfo.getCurrentRepetition();
        final String path = "/fail/" +i ;

        // when
        client.get(path);

        // then
        assertHasOneTransaction("GET /fail/:iteration", 500);
        assertFirstRequestContains(path, Objects::isNull);
        assertFirstError(UnsupportedOperationException.class);
    }

    @ParameterizedTest()
    @ValueSource(strings = { "/capture-body", "/capture-stream" })
    void shouldCaptureRequestBody(final String path) throws Exception {
        assert coreConfiguration != null;
        assert client != null;

        // when
        client.request(path, r -> r.body(body -> body.text("good morning!")).post());

        // then
        assertHasOneTransaction("POST " + path, 200);
        assertFirstRequestContains(path, body -> body.toString().equals("good morning!"));
    }

    @Test
    void shouldNotCaptureRequestBodyWhenNotSampling() throws Exception {
        assert coreConfiguration != null;
        assert client != null;

        // given
        // sampling is off
        coreConfiguration.getSampleRate().update(0d, SpyConfiguration.CONFIG_SOURCE_NAME);
        final String path = "/capture-body";

        // when
        client.request(path, r -> r.body(body -> body.text("good morning!")).post());

        // then
        assertHasOneTransaction("POST /capture-body", 0, false);
        assertFirstRequestContains(null, body -> body.toString().equals("[REDACTED]"));
    }

    @Test
    void shouldCaptureRequestStream() throws Exception{
        assert coreConfiguration != null;
        assert client != null;

        // given
        // sampling is on
        coreConfiguration.getSampleRate().update(1d, SpyConfiguration.CONFIG_SOURCE_NAME);
        final String path = "/capture-body";

        // when
        client.request(path, r -> r.body(body -> body.text("good morning!")).post());

        // then
        assertHasOneTransaction("POST /capture-body", 200);
        assertFirstRequestContains(path, body -> body.toString().equals("good morning!"));
    }

    @Test
    void shouldNotCaptureRequestBodyWhenCaptureBodyIsOFF() throws Exception {
        assert coreConfiguration != null;
        assert webConfiguration != null;
        assert client != null;

        // given
        // capture body is off
        when(webConfiguration.getCaptureBody()).thenReturn(WebConfiguration.EventType.OFF);
        final String path = "/capture-body";

        // when
        client.request(path, r -> r.body(body -> body.text("good morning!")).post());

        // then
        assertHasOneTransaction("POST /capture-body", 200);
        assertFirstRequestContains(path, body -> body.toString().equals("[REDACTED]"));
    }

    @Test
    void shouldNotCaptureIgnoredUrls() {
        assert client != null;
        assert webConfiguration != null;

        // given
        // happy path is ignored
        when(webConfiguration.getIgnoreUrls()).thenReturn(List.of(WildcardMatcher.valueOf("/happy/*")));
        final String path = "/happy/0";

        // when
        client.get(path);

        // then
        assertThat(reporter.getTransactions()).hasSize(0);
    }

    @Test
    void shouldNotCaptureIgnoredUserAgents() {
        assert client != null;
        assert webConfiguration != null;

        // given
        // happy path is ignored
        when(webConfiguration.getIgnoreUserAgents()).thenReturn(List.of(WildcardMatcher.valueOf("unit-test")));
        final String path = "/happy/0";

        // when
        client.request(path, requestSpec -> requestSpec.headers(mutableHeaders -> {
            mutableHeaders.add(HttpHeaderNames.USER_AGENT, "unit-test");
        }));

        // then
        assertThat(reporter.getTransactions()).hasSize(0);
    }

    private void assertHasOneTransaction(final String transactionName, final int status) throws InterruptedException {
        assertHasOneTransaction(transactionName, status, true);
    }

    private void assertHasOneTransaction(final String transactionName, final int status, final boolean finished) throws InterruptedException {

        assertThat(reporter.getTransactions()).hasSize(1);

        final Transaction firstTransaction = reporter.getFirstTransaction(500);

        assertThat(firstTransaction).isNotNull();
        assertThat(firstTransaction.getNameAsString()).isEqualTo(transactionName);
        assertThat(firstTransaction.getResult()).contains(String.valueOf(status));
        assertThat(firstTransaction.getContext().getResponse().isFinished()).isEqualTo(finished);
        assertThat(firstTransaction.getContext().getResponse().getStatusCode()).isEqualTo(status);

    }

    private void assertFirstRequestContains(@Nullable final String path, final Predicate<? super Object> bodyPredicate) {

        final TransactionContext context = reporter.getFirstTransaction().getContext();

        assertThat(context.getRequest().getUrl().getPathname()).isEqualTo(path);
        assertThat(context.getRequest().getBody()).matches(bodyPredicate);
    }

    private void assertFirstError(@SuppressWarnings("SameParameterValue") final Class<?> exceptionClass) {
        final ErrorCapture firstError = reporter.getFirstError();

        assertThat(firstError.getException()).isInstanceOf(exceptionClass);
    }

}
