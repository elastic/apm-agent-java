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
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.springwebflux.testapp.GreetingWebClient;
import co.elastic.apm.agent.springwebflux.testapp.WebFluxApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletContainerTest extends AbstractInstrumentationTest {

    protected static WebFluxApplication.App app;
    protected static GreetingWebClient client;

    @BeforeAll
    static void startApp() {

        // using tomcat explicitly as netty is not a servlet container
        app = WebFluxApplication.run(-1, "tomcat", true);

        // client type and server endpoint do not matter here
        client = app.getClient(true);
    }

    @AfterAll
    static void stopApp() {
        app.close();

        // reset context CL on all threads to remove any embedded tomcat CL that might influence tests afterwards
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            thread.setContextClassLoader(null);
        }
    }

    @AfterEach
    void after() {
        // force GC execution as reference counting relies on it

        AbstractServerInstrumentationTest.flushGcExpiry();
    }

    @Test
    void shouldOnlyCreateOneTransaction() throws InterruptedException {

        // using a request with path parameter so we are sure servlet path and webflux path template are not equal
        StepVerifier.create(client.withPathParameter("42"))
            .expectNext("Hello, 42!")
            .verifyComplete();

        // at least one transaction expected
        Transaction transaction = reporter.getFirstTransaction(200);

        // transaction naming should be set by webflux instrumentation
        AbstractServerInstrumentationTest.checkTransaction(transaction, "GET /functional/with-parameters/{id}", "GET", 200);

        // transaction HTTP part should be provided by servlet instrumentation
        AbstractServerInstrumentationTest.checkUrl(client, transaction, "/with-parameters/42");

        Thread.sleep(200);

        // but we expect exactly one transaction as there is only one HTTP request
        assertThat(reporter.getTransactions())
            .hasSize(1);

    }
}
