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

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import ratpack.http.client.HttpClient;
import ratpack.server.RatpackServer;
import ratpack.test.ServerBackedApplicationUnderTest;
import ratpack.test.http.TestHttpClient;

import javax.annotation.Nullable;
import java.net.URI;

public class RatpackHttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    @Nullable
    private static ServerBackedApplicationUnderTest proxy;

    @Nullable
    private static TestHttpClient client;

    @Override
    protected void startTransaction() {
        // suppressing start of transaction because transaction will be created within the Ratpack Server
    }

    @Override
    protected void endTransaction() {
        // suppressing end of transaction because transaction will be ended within the Ratpack Server
    }

    @BeforeClass
    public static void createServer() throws Exception {

        // Ratpack's HttpClient needs to be operated on Ratpack's managed thread pools, and can not
        // operate in isolation. Here, we're creating what is essentially a proxy server in order to
        // drive requests to the locations specified by the base test class.
        //
        // The ratpack proxy server is defining the Transaction boundaries.
        proxy = ServerBackedApplicationUnderTest.of(
            RatpackServer.of(s -> {
                s.serverConfig(c ->
                    c.port(0));
                s.handlers(c ->
                    c.all(ctx -> {
                        final URI uri = URI.create(ctx.getRequest().getQueryParams().get("uri"));
                        ctx.get(HttpClient.class)
                            .get(uri)
                            .then(response -> ctx.render(response.getBody().getText()));
                    }));
            })
        );

        client = proxy.getHttpClient();
    }

    @AfterClass
    public static void closeServer() {
        assert proxy != null;
        proxy.close();
    }

    @Override
    protected void performGet(final String path) {
        assert client != null;
        client
            .params(params -> params.put("uri", path))
            .get("/");
    }
}
