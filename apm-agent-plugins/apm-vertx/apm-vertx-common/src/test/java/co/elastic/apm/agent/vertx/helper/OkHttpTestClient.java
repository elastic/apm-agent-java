/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.vertx.helper;

import okhttp3.ConnectionSpec;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jetty.util.ssl.SslContextFactory.TRUST_ALL_CERTS;

public class OkHttpTestClient {
    @Nullable
    private final OkHttpClient httpClient;

    private final int port;

    private final boolean useSSL;

    private final String schema;

    private static final TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }
    };

    public OkHttpTestClient(boolean useSSL, int port) throws Exception {
        this.useSSL = useSSL;
        this.schema = useSSL ? "https" : "http";
        this.port = port;
        httpClient = initOkHttpClient();
    }

    private OkHttpClient initOkHttpClient() throws Exception {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (useSSL) {
            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, TRUST_ALL_CERTS, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            builder.connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true);
        }

        builder.readTimeout(0, TimeUnit.SECONDS)
                .connectTimeout(0, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();

        return builder.build();
    }

    public Response get(String path) throws IOException {
        return get(path, Collections.emptyMap());
    }
    public Response get(String path, Map<String, String> headers) throws IOException {
        return httpClient.newCall(new okhttp3.Request.Builder().url(schema + "://localhost:" + port + path).headers(Headers.of(headers)).build()).execute();
    }

    public Response post(String path, String requestBody, MediaType mediaType) throws IOException {
        return post(path, Collections.emptyMap(), requestBody, mediaType);
    }

    public Response post(String path, Map<String, String> headers, String requestBody, MediaType mediaType) throws IOException {
        return httpClient.newCall(new okhttp3.Request.Builder().url(schema + "://localhost:" + port + path).method("POST", RequestBody.create(requestBody, mediaType)).headers(Headers.of(headers)).build()).execute();
    }
}
