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
package co.elastic.apm.agent.reactor_netty;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.netty.ByteBufFlux;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import java.util.List;
import java.util.logging.Logger;

public class ReactorNettyTest extends AbstractInstrumentationTest {

    private static final Logger log = Logger.getLogger(ReactorNettyTest.class.getCanonicalName());

    @After
    public void after() {
        reporter.reset();
    }

    @Test
    public void shouldInstrumentPostRequest() {
        DisposableServer boundServer = HttpServer.create()
            .port(0)
            .route(routes ->
                routes.post("/test/{param}", (request, response) ->
                    response.sendString(request.receive()
                        .asString()
                        .map(s -> s + ' ' + request.param("param") + '!')
                        .log("http-server"))))
            .bindNow();

        HttpClient.create()
            .port(boundServer.port())
            .post()
            .uri("/test/World")
            .send(ByteBufFlux.fromString(Flux.just("Hello")))
            .responseContent()
            .aggregate()
            .asString()
            .log("http-client")
            .block();

        final List<Transaction> transactions = reporter.getTransactions();

        Assert.assertEquals(transactions.size(), 1);
        Assert.assertEquals(transactions.get(0).getNameAsString(), "POST /test/World");

        log.info("Request handled in: " + transactions.get(0).getDuration());

        boundServer.disposeNow();
    }

}
