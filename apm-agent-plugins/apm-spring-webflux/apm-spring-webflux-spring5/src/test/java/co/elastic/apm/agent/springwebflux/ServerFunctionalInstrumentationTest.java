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

import co.elastic.apm.agent.springwebflux.testapp.GreetingWebClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.test.StepVerifier;

public class ServerFunctionalInstrumentationTest extends AbstractServerInstrumentationTest {

    @Override
    protected GreetingWebClient getClient() {
        return app.getClient(true);
    }

    @ParameterizedTest
    @CsvSource({"/hello", "/hello2"})
    void shouldInstrumentSimpleGetRequest(String path) {
        StepVerifier.create(client.requestMono("GET", path, 200))
            .expectNext("Hello, Spring!")
            .verifyComplete();

        checkTransaction(getFirstTransaction(), "GET /functional" + path, "GET", 200);
    }

    @ParameterizedTest
    @CsvSource({"GET", "POST"})
    void shouldInstrumentNestedRoutes(String method) {
        StepVerifier.create(client.requestMono(method, "/nested", 200))
            .expectNext(String.format("Hello, nested %s!", method))
            .verifyComplete();

        checkTransaction(getFirstTransaction(), method + " /functional/nested", method, 200);
    }


}
