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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.SpanContext;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.springwebflux.testapp.WebFluxApplication;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

//@Timeout(value = 120, unit = TimeUnit.SECONDS)
public class WebFluxClientInstrumentationTest extends AbstractInstrumentationTest {

    protected static WebFluxApplication.App app;

    @BeforeAll
    static void startApp() {
        app = WebFluxApplication.run(-1, "netty", true);
    }

    @AfterAll
    static void stopApp() {
        if (app != null) {
            app.close();
        }
    }

    @BeforeEach
    public void setupTests() {
        startTestRootTransaction("parent of http span");
    }

    @AfterEach
    public void after() {
        Transaction transaction = tracer.currentTransaction();
        assertThat(transaction).isNotNull();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    static Function<String, WebClient> webClientFunction1 = (s) -> WebClient.create(s);
    static Function<String, WebClient> webClientFunction2 = (s) -> WebClient.builder().clientConnector(new ReactorClientHttpConnector())
        .baseUrl(s).build();
    static Function<String, WebClient> webClientFunction3 = (s) -> {
//        https://www.eclipse.org/jetty/documentation/jetty-9/index.html#http-client
        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        HttpClient httpClient = new HttpClient(sslContextFactory);
        try {
            httpClient.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return WebClient.builder().clientConnector(new JettyClientHttpConnector(httpClient)).baseUrl(s).build();
    };
    static BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction1 = (r, c) -> r.retrieve().bodyToFlux(c);
    static BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction2 = (r, c) -> ((ClientResponse) r.exchange()
        .block())
        .bodyToFlux(c);

    static BiFunction<WebClient.RequestHeadersSpec, Class, Stream<Flux>> bodyToFluxStreamFunction1 = (r, c) -> {
        WebClient.ResponseSpec response = r.retrieve();
        Flux f1 = response.bodyToFlux(c);
        Flux f2 = response.bodyToFlux(c);
        Flux f3 = response.bodyToFlux(c);
        return Stream.of(f1, f2, f3);
    };
    static BiFunction<WebClient.RequestHeadersSpec, Class, Stream<Flux>> bodyToFluxStreamFunction2 = (r, c) -> {
        ClientResponse response = (ClientResponse) r.exchange().block();
        Flux f1 = response.bodyToFlux(c);
        Flux f2 = response.bodyToFlux(c);
        Flux f3 = response.bodyToFlux(c);
        return Stream.of(f1, f2, f3);
    };

    static Stream<Arguments> testBodyRequestSource() {
        //body resources
        Resource resource = new ClassPathResource("response.txt");
        MultiValueMap<String, String> formDataBody = new LinkedMultiValueMap<>();
        formDataBody.set("name 1", "value 1");
        formDataBody.add("name 2", "value 2+1");
        formDataBody.add("name 2", "value 2+2");
        formDataBody.add("name 3", null);
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.put("name", Arrays.asList("value1", "value2"));

        DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
        DefaultDataBuffer dataBuffer =
            factory.wrap(ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8)));
        Flux<DataBuffer> dataBufferBody = Flux.just(dataBuffer);
        return Stream.of(
            //From Publisher
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
            //FIXME: investigate why 3
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
            //FIXME: investigate why 3
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            //Resource
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 5),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 5),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 5),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 6),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 5),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 5),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 5),
            //FormData
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-form-mapping", 3),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-form-mapping", 3),
            //MultiPart
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),//ERROR reference 1
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),//15?
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),//14?
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),//14
//            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 13),//15
            //DataBuffers
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),//4?
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            //Producers
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 1),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 1),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping", 1),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 4),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 4),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping", 4),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 4),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 4),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping", 4),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping", 3)

//            Arguments.of(BodyInserters.fromMultipartAsyncData(map), HttpMethod.POST ),

        );
    }

    static Stream<Arguments> testNonBodyRequest() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxFunction1, 5),
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxFunction2, 5),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxFunction1, 5),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxFunction2, 5),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxFunction1, 5),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxFunction2, 5),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction2, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction2, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 2)
        );
    }

    static Stream<Arguments> testNonBodyCancelRequestSource() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxFunction1, 7),
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxFunction2, 7),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxFunction1, 7),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxFunction2, 7),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxFunction1, 7),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxFunction2, 7),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction2, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction2, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 2)
        );
    }

    static Stream<Arguments> testErrorRequestSource() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxFunction1, 3),
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxFunction2, 3),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxFunction1, 3),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxFunction2, 3),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxFunction1, 3),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxFunction2, 3),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction2, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction2, 2),
            //FIXME: investigate why 1 span instead of 2
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction1, 1),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 1)
        );
    }

    static Stream<Arguments> testBackPressureRequestSource() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxFunction1, 4),
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxFunction2, 4),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxFunction1, 4),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxFunction2, 4),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxFunction1, 4),
            //FIXME: bit flaky
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxFunction2, 4),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction2, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction2, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 2)
        );
    }

    static Stream<Arguments> testNonBodyMultiRequestSource() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxStreamFunction1, 13),
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxStreamFunction2, 5),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxStreamFunction1, 13),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxStreamFunction2, 5),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxStreamFunction1, 13),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxStreamFunction2, 5),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxStreamFunction1, 4),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxStreamFunction2, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxStreamFunction1, 4),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxStreamFunction2, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxStreamFunction1, 4),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxStreamFunction2, 2)
        );
    }

    static Stream<Arguments> testErrorUriMethodSource() {
        return Stream.of(
            //incorrect host
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.GET, webClientFunction1, bodyToFluxFunction1, 1),
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.GET, webClientFunction1, bodyToFluxFunction2, 1),
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.GET, webClientFunction2, bodyToFluxFunction1, 1),
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.GET, webClientFunction2, bodyToFluxFunction2, 1),
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.GET, webClientFunction3, bodyToFluxFunction1, 1),
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.GET, webClientFunction3, bodyToFluxFunction2, 1),

            //incorrect port
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.GET, webClientFunction1, bodyToFluxFunction1, 1),
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.GET, webClientFunction1, bodyToFluxFunction2, 1),
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.GET, webClientFunction2, bodyToFluxFunction1, 1),
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.GET, webClientFunction2, bodyToFluxFunction2, 1),
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.GET, webClientFunction3, bodyToFluxFunction1, 1),
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.GET, webClientFunction3, bodyToFluxFunction2, 1),

            //incorrect scheme
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.GET, webClientFunction1, bodyToFluxFunction1, 1),
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.GET, webClientFunction1, bodyToFluxFunction2, 1),
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.GET, webClientFunction2, bodyToFluxFunction1, 1),
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.GET, webClientFunction2, bodyToFluxFunction2, 1),
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.GET, webClientFunction3, bodyToFluxFunction1, 1),//FIXME: PROBLEM? result 0
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.GET, webClientFunction3, bodyToFluxFunction2, 1),//FIXME: PROBLEM? result 0

            //incorrect uri
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.GET, webClientFunction1, bodyToFluxFunction1, 3),//TODO: investigate
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.GET, webClientFunction1, bodyToFluxFunction2, 3),
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.GET, webClientFunction2, bodyToFluxFunction1, 3),
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.GET, webClientFunction2, bodyToFluxFunction2, 3),
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.GET, webClientFunction3, bodyToFluxFunction1, 3),
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.GET, webClientFunction3, bodyToFluxFunction2, 3),

            //incorrect host
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction1, 1),
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction2, 1),
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction1, 1),
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction2, 1),
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction1, 1),
            Arguments.of("http://localhos", "/hello-mapping", HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 1),

            //incorrect port
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction1, 1),
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction2, 1),
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction1, 1),
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction2, 1),
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction1, 1),
            Arguments.of("http://localhost:9999", "/hello-mapping", HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 1),

            //incorrect scheme
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction1, 1),
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction2, 1),
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction1, 1),
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction2, 1),
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction1, 1),//FIXME: fix ssl dependency
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 1),

            //incorrect uri
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction1, 2),
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.HEAD, webClientFunction1, bodyToFluxFunction2, 2),
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction1, 2),
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.HEAD, webClientFunction2, bodyToFluxFunction2, 2),
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction1, 1),//FIXME:
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 1)//FIXME:
        );
    }

    @ParameterizedTest
    @MethodSource("testBodyRequestSource")
    public void testBodyRequest(BodyInserter<?, ? super ClientHttpRequest> inserter, HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction, String uri, int expected) {
        testTemplate(httpMethod, webClientFunction, "http://localhost:" + app.getPort(), uri,
            bodyToFluxFunction, inserter, null, String.class);
        verifySpans(30000L, expected);
    }

    @ParameterizedTest
    @MethodSource("testNonBodyRequest")
    public void testNonBodyRequest(HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                   BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction, int expected) {
        testTemplate(httpMethod, webClientFunction, "http://localhost:" + app.getPort(), "/annotated/child-flux-stream",
            bodyToFluxFunction, null, null, String.class);
        verifySpans(30000L, expected);
    }

    //FIXME: should not have any reference left, but has 3
    @Disabled
    @ParameterizedTest
    @MethodSource("testNonBodyMultiRequestSource")
    public void testNonBodyMultiRequest(HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                        BiFunction<WebClient.RequestHeadersSpec, Class, Stream<Flux>> bodyToFluxStreamFunction, int expected) {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        WebClient.RequestBodySpec requestBodySpec = webClientFunction.apply("http://localhost:" + app.getPort())
            .method(httpMethod)
            .uri("/annotated/child-flux-stream");

        bodyToFluxStreamFunction.apply(requestBodySpec, String.class)
            .forEach(f -> {
                f
                    .doOnEach(signal -> System.out.println("each " + f + " " + signal))
                    .doOnComplete(() ->
                        {
                            System.out.println("complete " + f);
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            countDownLatch.countDown();
                        }
                    )
                    .doOnError(
                        (t) ->
                        {
                            System.out.println("error " + f);
                            countDownLatch.countDown();
                        }
                    )
                    .subscribe();
            });

        verifySpans(30000L, expected);
    }

    @ParameterizedTest
    @MethodSource("testNonBodyRequest")
    public void testNonBodySSERequest(HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                      BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction, int expected) {
        testTemplate(httpMethod, webClientFunction, "http://localhost:" + app.getPort(), "/annotated/child-flux-stream/sse",
            bodyToFluxFunction, null, null, String.class);

        verifySpans(30000L, expected);
    }

    @ParameterizedTest
    @MethodSource("testNonBodyCancelRequestSource")
    public void testNonBodyCancelRequest(HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                         BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction, int expected) throws InterruptedException {
        Disposable disposable = testTemplate(httpMethod, webClientFunction, "http://localhost:" + app.getPort(), "/annotated/child-flux-stream?count=10&delay=1000",
            bodyToFluxFunction, null, null, String.class);

        new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                disposable.dispose();
            }
        }.run();

        verifySpans(30000L, expected);
    }

    @ParameterizedTest
    @MethodSource("testErrorUriMethodSource")
    public void testErrorUriRequest(String baseUrl, String uri, HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                    BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction, int expected) throws Exception {
        try {
            testTemplate(httpMethod, webClientFunction, baseUrl, uri,
                bodyToFluxFunction, null, null, String.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        verifySpans(30000L, expected);
    }

    @ParameterizedTest
    @MethodSource("testErrorRequestSource")
    public void testErrorRequest(HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                 BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction, int expected) throws InterruptedException {
        testTemplate(httpMethod, webClientFunction, "http://localhost:" + app.getPort(), "/annotated/error-handler",
            bodyToFluxFunction, null, null, String.class);
        verifySpans(30000L, expected);
    }

    @ParameterizedTest
    @MethodSource("testBackPressureRequestSource")
    public void testBackPressureRequest(HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                        BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction, int expected) throws InterruptedException {
        Subscriber subscriber = new Subscriber() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(2);
            }

            @Override
            public void onNext(Object o) {
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void onComplete() {
            }
        };

        testTemplate(httpMethod, webClientFunction, "http://localhost:" + app.getPort(), "/annotated/child-flux-stream",
            bodyToFluxFunction, null, subscriber, String.class);
        verifySpans(30000L, expected);
    }

    public Disposable testTemplate(HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                   String baseUrl, String uri,
                                   BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction,
                                   BodyInserter<?, ? super ClientHttpRequest> inserter,
                                   Subscriber subscriber, Class fluxType) {
        WebClient.RequestBodySpec requestBodySpec = webClientFunction.apply(baseUrl)
            .method(httpMethod)
            .uri(uri);

        WebClient.RequestHeadersSpec requestHeadersSpec;
        if (inserter != null) {
            requestHeadersSpec = requestBodySpec.body(inserter);
        } else {
            requestHeadersSpec = requestBodySpec;
        }

        Disposable disposable = null;
        try {
            if (subscriber != null) {
                bodyToFluxFunction.apply(requestHeadersSpec, fluxType)
                    .subscribe(subscriber);

            } else {
                disposable = bodyToFluxFunction.apply(requestHeadersSpec, fluxType)
                    .subscribe();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return disposable;
    }

    public void verifySpans(long assertTimeout, int expected) {
        reporter.awaitUntilAsserted(assertTimeout, () -> assertThat(
            reporter.getNumReportedSpans())
            .isGreaterThanOrEqualTo(expected));
        List<Span> spanList = reporter.getSpans();
        System.out.println("spanList=" + spanList.size() + " reporter.getTransactions()=" + reporter.getTransactions().size());
        for (Span s : spanList) {
            SpanContext spanContext = s.getContext();

            System.out.println("--" + s + " " + s.getOutcome());
            if (spanContext != null && spanContext.getHttp().getUrl() != null) {
                System.out.println("------" + spanContext.getHttp().getMethod() + " " + spanContext.getHttp().getUrl());
            }
        }
    }
}
