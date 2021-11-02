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
package co.elastic.apm.agent.webflux.client;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

public abstract class AbstractWebClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    protected final WebClient webClient;

    public AbstractWebClientInstrumentationTest() {
// reactor.netty.http.client.HttpClient#followRedirect(boolean) will redirect when 301|302|307|308
// by this reason I add workaround via BiConsumer.
        HttpClient httpClient = HttpClient.create().followRedirect((req, res) -> res.status().code() == 303);
        webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    @Override
    public boolean isRequireCheckErrorWhenCircularRedirect() {
        return false;
    }

    /**
     * reactor.core.Exceptions$ReactiveException: java.net.UnknownHostException: failed to resolve 'user:passwd@localhost' after 2 queries
     * <p>
     * at reactor.core.Exceptions.propagate(Exceptions.java:392)
     * at reactor.core.publisher.BlockingSingleSubscriber.blockingGet(BlockingSingleSubscriber.java:97)
     * at reactor.core.publisher.Mono.block(Mono.java:1706)
     * at co.elastic.apm.agent.webflux.client.WebClientExchangeFunctionInstrumentationTest.performGet(WebClientExchangeFunctionInstrumentationTest.java:31)
     * at co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest.testHttpCallWithUserInfo(AbstractHttpClientInstrumentationTest.java:138)
     * at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
     * at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
     * at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
     * at java.base/java.lang.reflect.Method.invoke(Method.java:566)
     * at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)
     * at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
     * at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:56)
     * at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
     * at org.junit.internal.runners.statements.RunBefores.evaluate(RunBefores.java:26)
     * at org.junit.internal.runners.statements.RunAfters.evaluate(RunAfters.java:27)
     *
     * @return
     */
    @Override
    public boolean isTestHttpCallWithUserInfoEnabled() {
        return false;
    }
}
