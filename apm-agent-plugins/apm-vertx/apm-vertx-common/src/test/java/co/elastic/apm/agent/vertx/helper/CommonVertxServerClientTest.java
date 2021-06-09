/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.vertx.helper;

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public abstract class CommonVertxServerClientTest extends AbstractVertxWebTest {

    private Vertx vertx;
    protected WebClient client;

    @ParameterizedTest
    @ValueSource(strings = {"basic", "oncontext", "blocking"})
    void testBasicDownstreamCall(String targetPath) throws Exception {
        http().get("/" + targetPath);

        reporter.awaitTransactionCount(2);
        reporter.awaitSpanCount(1);

        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions.stream().map(t -> t.getNameAsString())).containsExactly("GET /downstream", "GET /" + targetPath);

        Transaction firstTransaction = transactions.stream().filter(t -> t.getNameAsString().contains(targetPath)).findFirst().get();
        Transaction secondTransaction = transactions.stream().filter(t -> t.getNameAsString().contains("downstream")).findFirst().get();

        Span exitSpan = reporter.getFirstSpan();
        assertThat(exitSpan.isChildOf(firstTransaction)).isTrue();
        assertThat(secondTransaction.isChildOf(exitSpan)).isTrue();

        assertThat(exitSpan.getType()).isEqualTo("external");
        assertThat(exitSpan.getSubtype()).isEqualTo("http");
    }

    @ParameterizedTest
    @ValueSource(strings = {"basic", "oncontext", "blocking"})
    void testDownstreamCallWithExtraSpan(String targetPath) throws Exception {
        http().get("/with-extra-span/" + targetPath);

        reporter.awaitTransactionCount(2);
        reporter.awaitSpanCount(2);

        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions.stream().map(t -> t.getNameAsString())).containsExactly("GET /downstream", "GET /with-extra-span/" + targetPath);

        Transaction firstTransaction = transactions.stream().filter(t -> t.getNameAsString().contains(targetPath)).findFirst().get();
        Transaction secondTransaction = transactions.stream().filter(t -> t.getNameAsString().contains("downstream")).findFirst().get();

        List<Span> spans = reporter.getSpans();
        Span exitSpan = spans.stream().filter(s -> s.getNameAsString().contains("GET")).findFirst().get();
        Span customSpan = spans.stream().filter(s -> s.getNameAsString().equals("custom-child-span")).findFirst().get();

        assertThat(customSpan.isChildOf(firstTransaction)).isTrue();
        assertThat(exitSpan.isChildOf(customSpan)).isTrue();
        assertThat(secondTransaction.isChildOf(exitSpan)).isTrue();

        assertThat(exitSpan.getType()).isEqualTo("external");
        assertThat(exitSpan.getSubtype()).isEqualTo("http");
    }

    @BeforeEach
    public void setUpClient() {
        // This property is needed as otherwise Vert.x event loop threads won't have a context class loader (null)
        // which leads to NullPointerExceptions when spans are JSON validated in Unit tests
        System.setProperty("vertx.disableTCCL", "true");
        vertx = Vertx.vertx();
        client = WebClient.create(vertx);
    }

    @AfterEach
    public void close() {
        client.close();
        close(vertx);
    }

    @Override
    protected void initRoutes(Router router) {
        router.get("/downstream").handler(getDefaultHandlerImpl());
        router.get("/basic").handler(routingContext ->
            client.getAbs("http://localhost:" + port() + "/downstream").send(result ->
                getDefaultHandlerImpl().handle(routingContext)
            )
        );

        router.get("/oncontext").handler(routingContext -> routingContext.vertx()
                .runOnContext(v ->
                        client.getAbs("http://localhost:" + port() + "/downstream").send(result ->
                                getDefaultHandlerImpl().handle(routingContext))
                ));

        router.get("/blocking").handler(routingContext -> routingContext.vertx()
                .executeBlocking(tid ->
                        client.getAbs("http://localhost:" + port() + "/downstream").send(result ->
                                getDefaultHandlerImpl().handle(routingContext)), result -> {
                }));

        router.get("/with-extra-span/basic").handler(routingContext ->
                new HandlerWithCustomNamedSpan(rContext ->
                        client.getAbs("http://localhost:" + port() + "/downstream").send(result ->
                                getDefaultHandlerImpl().handle(rContext)), routingContext, "custom").handle(null));

        router.get("/with-extra-span/oncontext").handler(routingContext -> routingContext.vertx()
                .runOnContext(new HandlerWithCustomNamedSpan(rContext ->
                        client.getAbs("http://localhost:" + port() + "/downstream").send(result ->
                                getDefaultHandlerImpl().handle(rContext)), routingContext, "custom")
                ));

        router.get("/with-extra-span/blocking").handler(routingContext -> routingContext.vertx()
            .executeBlocking(tid -> new HandlerWithCustomNamedSpan(rContext ->
                    client.getAbs("http://localhost:" + port() + "/downstream").send(result ->
                        getDefaultHandlerImpl().handle(rContext)), routingContext, "custom").handle(null),
                result -> {
                }));
    }

    protected abstract Handler<RoutingContext> getDefaultHandlerImpl();

    protected abstract void close(Vertx vertx);

    @Override
    protected boolean useSSL() {
        return false;
    }
}
