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
package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.junit.Before;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class OkHttpClientAsyncInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private OkHttpClient client;

    @Before
    public void setUp() {
        client = new OkHttpClient();
    }

    @Override
    protected void performGet(String path) throws Exception {
        Request request = new Request.Builder()
            .url(path)
            .build();

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request req, IOException e) {
                countDownLatch.countDown();
            }

            @Override
            public void onResponse(Response response) throws IOException {
                countDownLatch.countDown();
                response.body().close();
            }
        });
        countDownLatch.await(1, TimeUnit.SECONDS);

    }

    @Override
    protected boolean isIpv6Supported() {
        // See https://github.com/square/okhttp/issues/2618
        return false;
    }
}

