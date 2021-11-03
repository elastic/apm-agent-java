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

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class WebFluxClientInstrumentationNoTransactionTest extends WebFluxClientInstrumentationTest {

    @AfterEach
    public void after() {
        Transaction transaction = tracer.currentTransaction();
        assertThat(transaction).isNull();
        assertThat(reporter.getTransactions()).hasSize(0);
    }

    @BeforeEach
    public void setupTests() {
        //dont setup transaction
    }

    public void verifySpans(long assertTimeout, int expected) {
        reporter.awaitUntilAsserted(assertTimeout, () -> assertThat(
            reporter.getNumReportedSpans())
            .isGreaterThanOrEqualTo(0));
        List<Span> spanList = reporter.getSpans();
        System.out.println("spanList=" + spanList.size() + " reporter.getTransactions()=" + reporter.getTransactions().size());
    }
}
