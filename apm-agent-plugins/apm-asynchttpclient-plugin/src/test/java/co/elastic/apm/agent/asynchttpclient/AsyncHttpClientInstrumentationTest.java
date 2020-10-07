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
package co.elastic.apm.agent.asynchttpclient;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncCompletionHandlerBase;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.handler.StreamedAsyncHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.asynchttpclient.Dsl.asyncHttpClient;

public class AsyncHttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private RequestExecutor requestExecutor;
    private AsyncHttpClient client;

    public static AsyncHandler<Response> customStreamAsyncHandler = new CustomStreamedAsyncHandler();

    public static Stream<Arguments> params() {
        List<RequestExecutor> requestExecutors = Arrays.asList(
                (client, path) -> client.executeRequest(new RequestBuilder().setUrl(path).build()).get(),
                (client, path) -> client.executeRequest(new RequestBuilder().setUrl(path).build(), new AsyncCompletionHandlerBase()).get(),
                (client, path) -> client.executeRequest(new RequestBuilder().setUrl(path).build(), customAsyncHandler).get(),
                (client, path) -> client.executeRequest(new RequestBuilder().setUrl(path).build(), customStreamAsyncHandler).get(),
                (client, path) -> client.prepareGet(path).execute(new AsyncCompletionHandlerBase()).get(),
                (client, path) -> client.prepareGet(path).execute().get()
        );
        return requestExecutors.stream().map(k -> Arguments.of(k)).collect(Collectors.toList()).stream();
    }

    @Override
    public void setUp(Object arg) {
        this.requestExecutor = (RequestExecutor) arg;
    }

    public static AsyncHandler<Response> customAsyncHandler = new AsyncCompletionHandler<Response>() {
        @Override
        public State onStatusReceived(HttpResponseStatus responseStatus) {
            assertThat(tracer.getActive()).isNotNull();
            assertThat(tracer.getActive().isExit()).isTrue();
            return State.CONTINUE;
        }

        @Override
        public void onThrowable(Throwable t) {
            assertThat(tracer.getActive()).isNotNull();
            assertThat(tracer.getActive().isExit()).isTrue();
        }

        @Override
        public Response onCompleted(Response response) {
            assertThat(tracer.getActive()).isNotNull();
            assertThat(tracer.getActive().isExit()).isTrue();
            return response;
        }

    };

    public static class CustomStreamedAsyncHandler extends AsyncCompletionHandler<Response> implements StreamedAsyncHandler<Response> {

        @Override
        public Response onCompleted(Response response) {
            assertThat(tracer.getActive()).isNotNull();
            assertThat(tracer.getActive().isExit()).isTrue();
            return response;
        }

        @Override
        public State onStream(Publisher<HttpResponseBodyPart> publisher) {
            assertThat(tracer.getActive()).isNotNull();
            assertThat(tracer.getActive().isExit()).isTrue();
            return State.ABORT;
        }
    }

    @BeforeEach
    public void setUp() {
        client = asyncHttpClient(Dsl.config()
                .setFollowRedirect(true)
                .build());
    }

    @AfterEach
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
