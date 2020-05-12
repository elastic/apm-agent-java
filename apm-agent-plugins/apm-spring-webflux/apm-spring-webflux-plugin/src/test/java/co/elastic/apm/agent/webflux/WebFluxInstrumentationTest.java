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
package co.elastic.apm.agent.webflux;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

public class WebFluxInstrumentationTest extends AbstractInstrumentationTest {

    public static final int PORT = 8081;
    // TODO: 06/05/2020 improvement: support random port for easier testing (without any spring-related test).

    @Nullable
    private static ConfigurableApplicationContext context;
    @Nullable
    private static GreetingWebClient client;

    @BeforeAll
    static void startApp() {
        context = GreetingApplication.run(PORT);
        client = GreetingApplication.getClient(context);
    }

    @AfterAll
    static void stopApp() {
        context.close();
    }

    // test cases to cover
    // -> transaction created when properly executing span
    // -  exception thrown during dispatch should properly terminate transaction

    @Test
    void simpleRequestDistpatch() {

        assertThat(reporter.getTransactions()).isEmpty();

        assertThat(client.getHelloMono()).isEqualTo("Hello, Spring!");

        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction).isNotNull();
    }
}
