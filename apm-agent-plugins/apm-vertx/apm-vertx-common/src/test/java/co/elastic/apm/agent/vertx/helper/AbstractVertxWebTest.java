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
package co.elastic.apm.agent.vertx.helper;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxTestContext;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractVertxWebTest extends AbstractInstrumentationTest {
    public static final String DEFAULT_RESPONSE_BODY = "Hello World!";
    protected static final String EXCEPTION_MESSAGE = "Test Exception";
    protected static final String INTERNAL_SERVER_ERROR = "Internal Server Error";
    protected static final String NOT_FOUND_RESPONSE_BODY = "<html><body><h1>Resource not found</h1></body></html>";

    protected static final String CALL_BLOCKING = "blocking";
    protected static final String CALL_SCHEDULED = "scheduled";
    protected static final String CALL_SCHEDULED_SHIFTED = "scheduled_shifted";
    protected static final String CALL_ON_CONTEXT = "on-context";

    protected WebConfiguration webConfiguration;
    protected CoreConfiguration coreConfiguration;

    @BeforeAll
    static void setCache() throws IOException {
        Path cache = Files.createTempDirectory("vertx.cache");
        System.setProperty("vertx.cacheDirBase", cache.toAbsolutePath().toString());
    }

    @BeforeEach
    void setUp() {
        webConfiguration = tracer.getConfig(WebConfiguration.class);
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
    }

    @Nullable
    private OkHttpTestClient httpClient;

    @Nullable
    private OkHttpTestClient httpsClient;

    @Nullable
    private VertxTestHttpServer server;

    @BeforeEach
    void init(VertxTestContext testContext) throws Throwable {
        server = new VertxTestHttpServer();
        initRoutes(server.getRouter());
        server.setup(testContext, useSSL());

        httpClient = new OkHttpTestClient(false, server.getPort());
        httpsClient = new OkHttpTestClient(true, server.getPort());

        // make sure mock reporter classes are loaded properly
        if (useSSL()) {
            https().get("/warmup");
        } else {
            http().get("/warmup");
        }
        reporter.awaitTransactionCount(1);
        cleanUp();
    }

    @AfterEach
    protected void stopServer(VertxTestContext testContext) throws Throwable {
        server.tearDown(testContext);
    }

    protected String schema() {
        return useSSL() ? "https" : "http";
    }

    protected int port() {
        return server.getPort();
    }

    protected abstract void initRoutes(final Router router);

    protected abstract boolean useSSL();


    public OkHttpTestClient http() {
        return httpClient;
    }

    public OkHttpTestClient https() {
        return httpsClient;
    }

    protected void expectTransaction(Response response, String path, String expectedResponseBody, String expectedTransactionName, int expectedStatusCode) throws IOException {
        assertThat(response.code()).isEqualTo(expectedStatusCode);
        assertThat(response.body().string()).isEqualTo(expectedResponseBody);

        reporter.getFirstTransaction(500);
        assertThat(reporter.getTransactions()
            .stream()
            .map(AbstractSpan::getNameAsString)
            .distinct())
            .describedAs("transaction service name should be inherited from test class name")
            .containsExactly(expectedTransactionName);
        TransactionContext context = reporter.getFirstTransaction().getContext();
        assertThat(context.getResponse().getStatusCode()).isEqualTo(expectedStatusCode);
        assertThat(context.getRequest().getUrl().getPathname()).isEqualTo(path);
    }
}
