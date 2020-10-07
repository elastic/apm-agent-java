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
package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import org.junit.jupiter.api.BeforeEach;

public class OkHttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private OkHttpClient client;

    @BeforeEach
    public void setUp() {
        client = new OkHttpClient();
    }

    @Override
    protected void performGet(String path) throws Exception {
        Request request = new Request.Builder()
                .url(path)
                .build();
        client.newCall(request).execute().body().close();
    }

    @Override
    protected boolean isIpv6Supported() {
        // see https://github.com/square/okhttp/issues/2618
        return false;
    }
}

