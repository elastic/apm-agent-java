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
package co.elastic.apm.agent.dubbo.api.impl;

import co.elastic.apm.agent.dubbo.api.DubboTestApi;
import co.elastic.apm.agent.dubbo.api.exception.BizException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.dubbo.rpc.AsyncContext;
import org.apache.dubbo.rpc.RpcContext;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class DubboTestApiImpl implements DubboTestApi {

    private static WireMockServer server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        try {
            MappingBuilder mappingBuilder = get(urlEqualTo("/")).willReturn(aResponse().withStatus(200));
            server.addStubMapping(mappingBuilder.build());
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private OkHttpClient client;

    private ExecutorService executorService;

    public DubboTestApiImpl() {
        client = new OkHttpClient();
        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public String normalReturn(String arg1, Integer arg2) {
        return arg1 + arg2;
    }

    @Override
    public String throwBizException(String arg1) {
        throw new BizException("test bizException");
    }

    @Override
    public String throwUnexpectedException(String arg1) {
        throw new RuntimeException("test unexpected exception");
    }

    @Override
    public String timeout(String arg) {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "timeout case";
    }

    @Override
    public String async(String arg1) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            //e.printStackTrace();
        }
        if ("error".equals(arg1)) {
            throw new RuntimeException();
        }
        return arg1;
    }

    @Override
    public void asyncNoReturn(String arg1) {
        doSomething();
    }

    @Override
    public CompletableFuture<String> asyncByFuture(final String arg1) {
        return CompletableFuture.supplyAsync(() -> {
            doSomething();
            return arg1;
        });
    }

    @Override
    public String asyncByAsyncContext(String arg1) {
        AsyncContext asyncContext = RpcContext.startAsync();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                doSomething();
                asyncContext.write(arg1);
            }
        });
        return null;
    }

    private void invokeHttp() {
        Request request = new Request.Builder()
            .url("http://localhost:" + server.port() + "/")
            .build();
        try {
            client.newCall(request).execute().body().close();
        } catch (IOException e) {

        }
    }

    private void doSomething() {
        try {
            Thread.sleep(100);
            invokeHttp();
        } catch (InterruptedException e) {
        }
    }
}
