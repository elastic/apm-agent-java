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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionAwareSubscriberTest extends AbstractInstrumentationTest {

    @Test
    void shouldCaptureResponseBeforeCancellingUpstream() {
        String headerName = "X-Header";
        String headerValue = "test";

        AtomicBoolean responseInvalidated = new AtomicBoolean();
        HttpHeaders responseHeaders = new HttpHeaders() {
            @Override
            public void forEach(BiConsumer<? super String, ? super List<String>> action) {
                if (responseInvalidated.get()) {
                    throw new NullPointerException("response headers have been invalidated");
                }
                super.forEach(action);
            }
        };
        responseHeaders.add(headerName, headerValue);

        AtomicBoolean upstreamCancelled = new AtomicBoolean();
        TransactionAwareSubscriber<Object> subscriber = createSubscriber(responseHeaders, new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                upstreamCancelled.set(true);
                responseInvalidated.set(true);
            }
        });

        subscriber.cancel();

        assertThat(upstreamCancelled).isTrue();
        assertThat(reporter.getFirstTransaction(500).getContext().getResponse().getHeaders()
            .getFirst(headerName)).isEqualTo(headerValue);
    }

    @Test
    void shouldCancelUpstreamAndEndTransactionWhenResponseHeaderCaptureFails() {
        HttpHeaders responseHeaders = new HttpHeaders() {
            @Override
            public void forEach(BiConsumer<? super String, ? super List<String>> action) {
                throw new NullPointerException("response headers have been invalidated");
            }
        };

        AtomicBoolean upstreamCancelled = new AtomicBoolean();
        TransactionAwareSubscriber<Object> subscriber = createSubscriber(responseHeaders, new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                upstreamCancelled.set(true);
            }
        });

        subscriber.cancel();

        assertThat(upstreamCancelled).isTrue();
        assertThat(reporter.getFirstTransaction(500)).isNotNull();
    }

    private TransactionAwareSubscriber<Object> createSubscriber(HttpHeaders responseHeaders, Subscription subscription) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(URI.create("http://localhost/cancelled"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        when(request.getCookies()).thenReturn(new LinkedMultiValueMap<>());

        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(responseHeaders);

        TransactionImpl transaction = tracer.startRootTransaction(null);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebfluxHelper.TRANSACTION_ATTRIBUTE, transaction);

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getAttributes()).thenReturn(attributes);
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);

        @SuppressWarnings("unchecked")
        CoreSubscriber<Object> downstream = mock(CoreSubscriber.class);
        when(downstream.currentContext()).thenReturn(Context.empty());

        TransactionAwareSubscriber<Object> subscriber =
            new TransactionAwareSubscriber<>(downstream, transaction, exchange, "test");
        subscriber.onSubscribe(subscription);
        return subscriber;
    }
}
