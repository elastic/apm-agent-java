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
package co.elastic.apm.agent.springwebclient;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import co.elastic.apm.agent.testutils.JUnit4TestClassWithDependencyRunner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.Arrays;
import java.util.List;

public class WebClientInstrumentationIT {


    @ParameterizedTest
    @ValueSource(strings = {"5.3.26"})
    public void testNetty(String springVersion) throws Exception {
        List<String> dependencies = Arrays.asList(
            "org.springframework:spring-web:" + springVersion,
            "org.springframework:spring-webflux:" + springVersion,
            "org.springframework:spring-core:" + springVersion,
            "org.springframework:spring-beans:" + springVersion
        );
        JUnit4TestClassWithDependencyRunner runner = new JUnit4TestClassWithDependencyRunner(dependencies, WebClientInstrumentationIT.class.getName() + "$TestImpl", WebClientInstrumentationIT.class.getName());
        runner.run();
    }

    /**
     * We don't test with all variations of {@link WebClientInstrumentationTest}
     * but just with netty for integration test.
     */
    public static class TestImpl extends AbstractHttpClientInstrumentationTest {

        private final WebClient webClient;


        public TestImpl() {
            HttpClient httpClient = HttpClient.create()
                // followRedirect(boolean) only enables redirect for 30[1278], not 303
                .followRedirect((req, res) -> res.status().code() == 303);

            // crete netty reactor client
            webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        }

        @Override
        public boolean isRequireCheckErrorWhenCircularRedirect() {
            // circular redirect does not trigger an error to capture with netty
            return false;
        }

        @Override
        public boolean isTestHttpCallWithUserInfoEnabled() {
            // user info URI does not work with netty
            return false;
        }


        @Override
        protected void performGet(String path) throws Exception {
            webClient.get().uri(path).exchangeToMono(response -> response.bodyToMono(String.class)).block();
        }

        @Override
        protected boolean isBodyCapturingSupported() {
            return true;
        }

        @Override
        protected void performPost(String path, byte[] content, String contentTypeHeader) throws Exception {
            webClient.post().uri(path)
                .header("Content-Type", contentTypeHeader)
                .body(Mono.just(content), byte[].class)
                .exchangeToMono(response -> response.bodyToMono(String.class)).block();
        }
    }
}
