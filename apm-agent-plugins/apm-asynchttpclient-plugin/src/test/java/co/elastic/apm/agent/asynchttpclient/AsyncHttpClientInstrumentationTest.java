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
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.RequestBuilder;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class AsyncHttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private AsyncHttpClient client;

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
        client.executeRequest(new RequestBuilder()
            .setUrl(path)
            .build()).get();
    }

}
