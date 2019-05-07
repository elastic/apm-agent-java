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
package co.elastic.apm.agent.asynchttpclient;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.asynchttpclient.AsyncCompletionHandlerBase;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.RequestBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;

import static org.asynchttpclient.Dsl.asyncHttpClient;

@RunWith(Parameterized.class)
public class AsyncHttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private final RequestExecutor requestExecutor;
    private AsyncHttpClient client;

    public AsyncHttpClientInstrumentationTest(RequestExecutor requestExecutor) {
        this.requestExecutor = requestExecutor;
    }

    @Parameterized.Parameters()
    public static Iterable<RequestExecutor> data() {
        return Arrays.asList(
            (client, path) -> client.executeRequest(new RequestBuilder().setUrl(path).build()).get(),
            (client, path) -> client.executeRequest(new RequestBuilder().setUrl(path).build(), new AsyncCompletionHandlerBase()).get(),
            (client, path) -> client.prepareGet(path).execute(new AsyncCompletionHandlerBase()).get(),
            (client, path) -> client.prepareGet(path).execute().get()
        );
    }

    @Before
    public void setUp() {
        client = asyncHttpClient(Dsl.config()
            .setFollowRedirect(true)
            .build());
    }

    @After
    public void tearDown() throws IOException {
        client.close();
    }

    @Override
    protected void performGet(String path) throws Exception {
        requestExecutor.execute(client, path);
    }

    interface RequestExecutor {
        void execute(AsyncHttpClient client, String path) throws Exception;
    }

}
