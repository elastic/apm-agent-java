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

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxTestContext;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class VertxTestHttpServer {

    protected Vertx vertx;

    private static final Logger logger = LoggerFactory.getLogger(VertxTestHttpServer.class);

    @Nullable
    private HttpServer server;

    private final Router router;


    VertxTestHttpServer() {
        vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(5));
        router = Router.router(vertx);
    }


    public void setup(VertxTestContext testContext, boolean useSSL) throws Throwable {
        HttpServerOptions serverOptions = new HttpServerOptions();

        if (useSSL) {
            serverOptions.setSsl(true)
                .setKeyCertOptions(new PemKeyCertOptions().setCertPath("tls/server-cert.pem").setKeyPath("tls/server-key.pem"))
                .setUseAlpn(true);
        }

        server = vertx.createHttpServer(serverOptions);
        server.requestHandler(router).listen(0, testContext.succeedingThenComplete());
        assertThat(testContext.awaitCompletion(2, TimeUnit.SECONDS)).isTrue();
        if (testContext.failed()) {
            logger.error("starting vertx server on port {} failed", server.actualPort());
            throw testContext.causeOfFailure();
        }

        logger.info("starting vertx server on port {} succeeded", server.actualPort());

    }

    public void tearDown(VertxTestContext testContext) throws Throwable {
        Objects.requireNonNull(server)
            .close(testContext.succeedingThenComplete());

        assertThat(testContext.awaitCompletion(2, TimeUnit.SECONDS)).isTrue();
        if (testContext.failed()) {
            logger.error("stopping vertx server on port {} failed", server.actualPort());
            throw testContext.causeOfFailure();
        }
        logger.info("stopping vertx server on port {} succeeded", server.actualPort());
    }

    public int getPort() {
        return server.actualPort();
    }

    public Router getRouter() {
        return router;
    }
}
