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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.springwebflux.testapp.WebFluxApplication;
import co.elastic.apm.agent.util.ExecutorUtils;
import org.junit.Ignore;
import org.junit.jupiter.api.*;
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
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


//@ExtendWith(SpringExtension.class)
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@ContextConfiguration
//@AutoConfigureWebFlux
//@AutoConfigureWebTestClient
//@EnableAutoConfiguration
public class WebFluxClientInstrumentationTest extends AbstractInstrumentationTest {

    protected static WebFluxApplication.App app;

    @BeforeAll
    static void startApp() {
        app = WebFluxApplication.run(-1, "netty", true);
    }

    @AfterAll
    static void stopApp() {
        app.close();
    }

    @BeforeEach
    public void setupTests() {
        startTestRootTransaction("parent of http span");
    }

    @AfterEach
    public final void after() {
        Transaction transaction = tracer.currentTransaction();
        assertThat(transaction).isNotNull();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);

    }

    static void flushGcExpiry() {
    }

    //TODO: send object body
    private static class User {

        //        @JsonView(SafeToSerialize.class)
        private String username;

        private String password;

        public User() {
        }

        public User(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    static Function<String, WebClient> webClientFunction1 = (s) -> {
        return WebClient.create(s);
    };
    static Function<String, WebClient> webClientFunction2 = (s) -> {
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector()).baseUrl(s).build();
    };
    static Function<String, WebClient> webClientFunction3 = (s) -> {
        return WebClient.builder().clientConnector(new JettyClientHttpConnector()).baseUrl(s).build();
    };
    static BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction1 = (r, c) -> r.retrieve().bodyToFlux(c);
    static BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction2 = (r, c) -> ((ClientResponse) r.exchange().block()).bodyToFlux(c);

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
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromPublisher(Flux.range(1, 10), Integer.class), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            //Resource
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromResource(resource), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),

            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-form-mapping"),
            Arguments.of(BodyInserters.fromFormData(formDataBody), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-form-mapping"),
            //MultiPart
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromMultipartData(map), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            //DataBuffers
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromDataBuffers(dataBufferBody), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            //Producers
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction1, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction1, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction2, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping"),
            Arguments.of(BodyInserters.fromProducer(Flux.just("foo"), String.class), HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, "/annotated/hello-body-mapping")

//            Arguments.of(BodyInserters.fromMultipartAsyncData(map), HttpMethod.POST ),

        );
    }

    static Stream<Arguments> testNonBodyRequest() {
        return Stream.of(
            //TEST
//            Arguments.of(HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, 2),//PUT shouldnt have return body, returns 5
//            Arguments.of(HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, 2),
//            Arguments.of(HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, 2),
//
//            Arguments.of(HttpMethod.TRACE, webClientFunction3, bodyToFluxFunction2, 2),
//            Arguments.of(HttpMethod.OPTIONS, webClientFunction3, bodyToFluxFunction2, 2),
//            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 2),
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
            //TEST
//            Arguments.of(HttpMethod.PUT, webClientFunction3, bodyToFluxFunction2, 2),//PUT shouldnt have return body, returns 5
//            Arguments.of(HttpMethod.POST, webClientFunction3, bodyToFluxFunction2, 2),
//            Arguments.of(HttpMethod.PATCH, webClientFunction3, bodyToFluxFunction2, 2),
//
//            Arguments.of(HttpMethod.TRACE, webClientFunction3, bodyToFluxFunction2, 2),
//            Arguments.of(HttpMethod.OPTIONS, webClientFunction3, bodyToFluxFunction2, 2),
//            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 2),
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
            //FIXME: jetty clients failing consistently with 1 span instead of 2
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction1, 2),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 2)
        );
    }

    static Stream<Arguments> testBackPressureRequestSource() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxFunction1, 4),
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxFunction2, 4),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxFunction1, 4),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxFunction2, 4),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxFunction1, 4),
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
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxStreamFunction1, 15),
            Arguments.of(HttpMethod.GET, webClientFunction1, bodyToFluxStreamFunction2, 5),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxStreamFunction1, 15),
            Arguments.of(HttpMethod.GET, webClientFunction2, bodyToFluxStreamFunction2, 5),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxStreamFunction1, 15),
            Arguments.of(HttpMethod.GET, webClientFunction3, bodyToFluxStreamFunction2, 5),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxStreamFunction1, 15),
            Arguments.of(HttpMethod.HEAD, webClientFunction1, bodyToFluxStreamFunction2, 5),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxStreamFunction1, 15),
            Arguments.of(HttpMethod.HEAD, webClientFunction2, bodyToFluxStreamFunction2, 5),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxStreamFunction1, 15),
            Arguments.of(HttpMethod.HEAD, webClientFunction3, bodyToFluxStreamFunction2, 5)
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
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.GET, webClientFunction3, bodyToFluxFunction1, 0),//FIXME: PROBLEM? result 0
            Arguments.of("https://localhost:" + app.getPort() + "", "/hello-mapping", HttpMethod.GET, webClientFunction3, bodyToFluxFunction2, 0),//FIXME: PROBLEM? result 0

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
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction1, 2),//FIXME:
            Arguments.of("http://localhost:" + app.getPort() + "", "/404", HttpMethod.HEAD, webClientFunction3, bodyToFluxFunction2, 2)//FIXME:
        );
    }

    @ParameterizedTest
    @MethodSource("testBodyRequestSource")
    public void testBodyRequest(BodyInserter<?, ? super ClientHttpRequest> inserter, HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction, String uri) throws InterruptedException {
        testTemplate(httpMethod, webClientFunction, "http://localhost:" + app.getPort(), uri,
            bodyToFluxFunction, inserter, null, String.class);
        verifySpans(30000L, 13);
    }

    @ParameterizedTest
    @MethodSource("testNonBodyRequest")
    public void testNonBodyRequest(HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                   BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction, int expected) throws InterruptedException {
        testTemplate(httpMethod, webClientFunction, "http://localhost:" + app.getPort(), "/annotated/child-flux-stream",
            bodyToFluxFunction, null, null, String.class);
        verifySpans(30000L, expected);
    }

    //FIXME: a bit flaky
    @ParameterizedTest
    @MethodSource("testNonBodyMultiRequestSource")
    public void testNonBodyMultiRequest(HttpMethod httpMethod, Function<String, WebClient> webClientFunction,
                                        BiFunction<WebClient.RequestHeadersSpec, Class, Stream<Flux>> bodyToFluxStreamFunction, int expected) throws InterruptedException {
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
                                      BiFunction<WebClient.RequestHeadersSpec, Class, Flux> bodyToFluxFunction, int expected) throws InterruptedException {
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


    //TODO: multiple different method
    @Ignore
    @Test
    public void testNonBodyMultiClientRequest() {
    }

//    @Test
    public void testNonBodyMultiRequestRetrieve() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        WebClient wc = WebClient.create("http://localhost:" + app.getPort());
        WebClient wc2 = WebClient.create("http://localhost:" + app.getPort());

        WebClient.RequestBodyUriSpec requestBodySpec1 = wc
            .method(HttpMethod.GET);
        //2 uris
        WebClient.RequestBodySpec requestBodySpec1_1 = requestBodySpec1.uri("/annotated/child-flux-stream");//GET stream
        WebClient.ResponseSpec responseSpec1 = requestBodySpec1_1 //GET stream with header
            .header("stuff", "foo")
            .retrieve();
        WebClient.ResponseSpec responseSpec2 = requestBodySpec1_1//GET stream
            .retrieve();

        WebClient.RequestBodySpec requestBodySpec1_2 = requestBodySpec1.uri("/annotated/child-flux-stream2");//GET stream2
        //4 requests
        WebClient.ResponseSpec responseSpec3 = requestBodySpec1_2 //GET stream2 with header
            .header("stuff", "bar")
            .retrieve();
        WebClient.ResponseSpec responseSpec4 = requestBodySpec1_2//GET stream2
            .retrieve();

        CountDownLatch countDownLatch1 = new CountDownLatch(3);
        responseSpec1.bodyToFlux(String.class)
            .doOnComplete(() -> {
                countDownLatch1.countDown();
            })
            .subscribe()
        ;
        responseSpec3.bodyToFlux(String.class)
            .doOnComplete(() -> {
                countDownLatch1.countDown();
            })
            .subscribe()
        ;
        responseSpec3.bodyToFlux(String.class)
            .doOnComplete(() -> {
                countDownLatch1.countDown();
            })
            .subscribe()
        ;
        countDownLatch1.await();
        try{
            Thread.sleep(30000);
        }catch(Exception e){
            e.printStackTrace();
        }

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

//    @Test
    public void testNonBodyMultiRequestExchange2() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        WebClient wc = WebClient.create("http://localhost:" + app.getPort());
        WebClient wc2 = WebClient.create("http://localhost:" + app.getPort());

        WebClient.RequestBodyUriSpec requestBodySpec1 = wc
            .method(HttpMethod.GET);
        //2 uris
        WebClient.RequestBodySpec requestBodySpec1_1 = requestBodySpec1.uri("/annotated/child-flux-stream");//GET stream
//        WebClient.ResponseSpec responseSpec1 = requestBodySpec1_1 //GET stream with header
//            .header("stuff", "foo")
//            .retrieve();
//        WebClient.ResponseSpec responseSpec2 = requestBodySpec1_1//GET stream
//            .retrieve();
        Mono<ClientResponse> responseSpec1 = requestBodySpec1_1 //GET stream with header
            .header("stuff", "foo")
            .exchange();
        WebClient.ResponseSpec responseSpec2 = requestBodySpec1_1//GET stream
            .retrieve();

        WebClient.RequestBodySpec requestBodySpec1_2 = requestBodySpec1.uri("/annotated/child-flux-stream2");//GET stream2
        //4 requests
        Mono<ClientResponse> responseSpec3 = requestBodySpec1_2 //GET stream2 with header
            .header("stuff", "bar")
            .exchange();
        WebClient.ResponseSpec responseSpec4 = requestBodySpec1_2//GET stream2
            .retrieve();

        CountDownLatch countDownLatch1 = new CountDownLatch(3);
        responseSpec1.block().bodyToFlux(String.class)
            .doOnComplete(() -> {
                countDownLatch1.countDown();
            })
            .subscribe()
        ;
        responseSpec3.block().bodyToFlux(String.class)
            .doOnComplete(() -> {
                countDownLatch1.countDown();
            })
            .subscribe()
        ;
        responseSpec3.block().bodyToFlux(String.class)
            .doOnComplete(() -> {
                countDownLatch1.countDown();
            })
            .subscribe()
        ;
        countDownLatch1.await();

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
    //Single client multiple successive calls

//    @Test
    public void testNonBodyMultiRequest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        WebClient wc = WebClient.create("http://localhost:" + app.getPort());
        WebClient wc2 = WebClient.create("http://localhost:" + app.getPort());

        WebClient.RequestBodyUriSpec requestBodySpec1 = wc
            .method(HttpMethod.GET);
        //2 uris
        WebClient.RequestBodySpec requestBodySpec1_1 = requestBodySpec1.uri("/annotated/child-flux-stream");//GET stream
        WebClient.ResponseSpec responseSpec1 = requestBodySpec1_1 //GET stream with header
            .header("stuff", "foo")
            .retrieve();
        WebClient.ResponseSpec responseSpec2 = requestBodySpec1_1//GET stream
            .retrieve();

        WebClient.RequestBodySpec requestBodySpec1_2 = requestBodySpec1.uri("/annotated/child-flux-stream2");//GET stream2
        //4 requests
        WebClient.ResponseSpec responseSpec3 = requestBodySpec1_2 //GET stream2 with header
            .header("stuff", "bar")
            .retrieve();
        WebClient.ResponseSpec responseSpec4 = requestBodySpec1_2//GET stream2
            .retrieve();

        //8 fluxes
        Flux f1 = responseSpec1.bodyToFlux(String.class);
        Flux f2 = responseSpec1.bodyToFlux(String.class);
        Flux f3 = responseSpec2.bodyToFlux(String.class);
        Flux f4 = responseSpec2.bodyToFlux(String.class);
        Flux f5 = responseSpec3.bodyToFlux(String.class);
        Flux f6 = responseSpec3.bodyToFlux(String.class);
        Flux f7 = responseSpec4.bodyToFlux(String.class);
        Flux f8 = responseSpec4.bodyToFlux(String.class);
        //16 subscribes, 16*5 80 spans
        f1.subscribe();//logprefix1
        f1.subscribe();//logprefix1
        f2.subscribe();//logprefix1
        f2.subscribe();//logprefix1

        f3.subscribe();//logprefix2
        f3.subscribe();//logprefix2
        f4.subscribe();//logprefix2
        f4.subscribe();//logprefix2

        f5.subscribe();//logprefix3
        f5.subscribe();//logprefix3
        f6.subscribe();//logprefix3
        f6.subscribe();//logprefix3

        f7.subscribe();//logprefix4
        f7.subscribe();//logprefix4
        f8.subscribe();//logprefix4
        f8.subscribe();//logprefix4

        WebClient.RequestBodyUriSpec requestBodySpec2 = wc
            .method(HttpMethod.HEAD);
        Mono<ClientResponse> clientResponseMono3 = requestBodySpec2.uri("/annotated/child-flux-stream").exchange();
        Mono<ClientResponse> clientResponseMono4 = requestBodySpec2.uri("/annotated/child-flux-stream2").exchange();

        countDownLatch.await(30, TimeUnit.SECONDS);
//        reporter.awaitUntilAsserted(30000L, () -> assertThat(
//
//            reporter.getNumReportedSpans())
//            .isEqualTo(6));
        verifySpans(30000L, 80);
    }

    //@Test
    public void testNonBodyMultiRequestExchange() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        WebClient wc = WebClient.create("http://localhost:" + app.getPort());
        WebClient wc2 = WebClient.create("http://localhost:" + app.getPort());

        WebClient.RequestBodyUriSpec requestBodySpec1 = wc
            .method(HttpMethod.GET);
        //2 uris
        WebClient.RequestBodySpec requestBodySpec1_1 = requestBodySpec1.uri("/annotated/child-flux-stream");//GET stream
        Mono<ClientResponse> responseSpec1 = requestBodySpec1_1 //GET stream with header
            .header("stuff", "foo")
            .exchange();
        responseSpec1.block().bodyToFlux(String.class);

        //will be overwritten??
        WebClient.RequestBodySpec requestBodySpec1_2 = requestBodySpec1.uri("/annotated/child-flux-stream2");//GET stream2
        //4 requests

        Mono<ClientResponse>  responseSpec2 = requestBodySpec1_1//GET stream
            .exchange();
        Mono<ClientResponse>  responseSpec3 = requestBodySpec1_2 //GET stream2 with header
            .header("stuff", "bar")
            .exchange();
        responseSpec3.block().bodyToFlux(String.class);
        Mono<ClientResponse>  responseSpec4 = requestBodySpec1_2//GET stream2
            .exchange();
/*
        //8 fluxes
        Flux f1 = responseSpec1.block().bodyToFlux(String.class);
        Flux f2 = responseSpec1.block().bodyToFlux(String.class);
        Flux f3 = responseSpec2.block().bodyToFlux(String.class);
        Flux f4 = responseSpec2.block().bodyToFlux(String.class);
        Flux f5 = responseSpec3.block().bodyToFlux(String.class);
        Flux f6 = responseSpec3.block().bodyToFlux(String.class);
        Flux f7 = responseSpec4.block().bodyToFlux(String.class);
        Flux f8 = responseSpec4.block().bodyToFlux(String.class);
        //16 subscribes, 16*5 80 spans
        f1.subscribe();//logprefix1
        f1.subscribe();//logprefix1
        f2.subscribe();//logprefix1
        f2.subscribe();//logprefix1

        f3.subscribe();//logprefix2
        f3.subscribe();//logprefix2
        f4.subscribe();//logprefix2
        f4.subscribe();//logprefix2

        f5.subscribe();//logprefix3
        f5.subscribe();//logprefix3
        f6.subscribe();//logprefix3
        f6.subscribe();//logprefix3

        f7.subscribe();//logprefix4
        f7.subscribe();//logprefix4
        f8.subscribe();//logprefix4
        f8.subscribe();//logprefix4
*/
//        WebClient.RequestBodyUriSpec requestBodySpec2 = wc
//            .method(HttpMethod.HEAD);
//        Mono<ClientResponse> clientResponseMono3 = requestBodySpec2.uri("/annotated/child-flux-stream").exchange();
//        Mono<ClientResponse> clientResponseMono4 = requestBodySpec2.uri("/annotated/child-flux-stream2").exchange();

        countDownLatch.await(30, TimeUnit.SECONDS);
//        reporter.awaitUntilAsserted(30000L, () -> assertThat(
//
//            reporter.getNumReportedSpans())
//            .isEqualTo(6));
        verifySpans(30000L, 80);
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
        if (subscriber != null) {
            bodyToFluxFunction.apply(requestHeadersSpec, fluxType)
                .subscribe(subscriber);

        } else {
            disposable = bodyToFluxFunction.apply(requestHeadersSpec, fluxType)
                .subscribe();
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
