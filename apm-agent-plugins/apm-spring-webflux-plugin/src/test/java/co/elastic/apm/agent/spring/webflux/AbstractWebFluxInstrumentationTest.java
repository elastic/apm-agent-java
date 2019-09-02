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
package co.elastic.apm.agent.spring.webflux;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import io.undertow.Undertow;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.UndertowHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

public class AbstractWebFluxInstrumentationTest extends AbstractInstrumentationTest {

    protected static Undertow startServer(RouterFunction<ServerResponse> route) {
        final HttpHandler httpHandler = RouterFunctions.toHttpHandler(route);

        final UndertowHttpHandlerAdapter undertowHttpHandlerAdapter = new UndertowHttpHandlerAdapter(httpHandler);

        final Undertow server = Undertow.builder()
            .addHttpListener(8200, "127.0.0.1")
            .setHandler(undertowHttpHandlerAdapter)
            .build();

        server.start();

        return server;
    }
}
