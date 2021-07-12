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
package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import co.elastic.apm.agent.util.Version;
import co.elastic.apm.agent.util.VersionUtils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class OkHttp3ClientAsyncInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private OkHttpClient client;
    private Version okhttpVersion;

    @Before
    public void setUp() {
        client = new OkHttpClient();
        String versionString = VersionUtils.getVersion(OkHttpClient.class, "com.squareup.okhttp3", "okhttp");
        okhttpVersion = Version.of(Objects.requireNonNullElse(versionString, "4.0.0"));
    }

    @Override
    protected boolean isErrorOnCircularRedirectSupported() {
        return okhttpVersion.compareTo(Version.of("3.6.0")) > -1;
    }

    @Override
    protected boolean isIpv6Supported() {
        return okhttpVersion.compareTo(Version.of("3.3.0")) > -1;
    }

    @Override
    protected void performGet(String path) throws Exception {
        Request request = new Request.Builder()
            .url(path)
            .build();

        final CompletableFuture<Void> future = new CompletableFuture<>();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    response.body().close();
                } finally {
                    future.complete(null);
                }
            }
        });
        future.get();
    }

}

