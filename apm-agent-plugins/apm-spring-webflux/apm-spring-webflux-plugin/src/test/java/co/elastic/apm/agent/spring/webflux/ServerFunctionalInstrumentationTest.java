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
package co.elastic.apm.agent.spring.webflux;

import co.elastic.apm.agent.spring.webflux.testapp.GreetingWebClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ServerFunctionalInstrumentationTest extends AbstractServerInstrumentationTest {

    @Override
    protected GreetingWebClient getClient() {
        return app.getClient(true);
    }

    @ParameterizedTest
    @CsvSource({"/hello", "/hello2"})
    void shouldInstrumentSimpleGetRequest(String path) {
        client.executeAndCheckRequest("GET", path, 200);

        checkTransaction(getFirstTransaction(), "/functional" + path, "GET", 200);
    }

    @ParameterizedTest
    @CsvSource({"GET", "POST"})
    void shouldInstrumentNestedRoutes(String method) {
        client.executeAndCheckRequest(method, "/nested", 200);

        checkTransaction(getFirstTransaction(), "/functional/nested", method, 200);
    }

    @Test
    void shouldInstrumentPathWithParameters() {
        client.withPathParameter("1234");

        checkTransaction(getFirstTransaction(), "/functional/with-parameters/{id}", "GET", 200);

        // TODO : add assertions to make sure request URL contains path variables
    }
}
