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
package specs;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;

import static org.assertj.core.api.Assertions.assertThat;

public class OutcomeState {

    private final ElasticApmTracer tracer;
    private Transaction transaction;
    private Span span;

    public OutcomeState() {
        tracer = MockTracer.createRealTracer();
    }

    public ElasticApmTracer getTracer() {
        return tracer;
    }

    public void startRootTransactionIfRequired() {
        if (transaction == null) {
            startTransaction();
        }
    }

    public Transaction startTransaction() {
        assertThat(transaction)
            .describedAs("transaction already set")
            .isNull();
        transaction = tracer.startRootTransaction(getClass().getClassLoader());

        return transaction;
    }


    public Span startSpan() {
        assertThat(span)
            .describedAs("span already set b")
            .isNull();

        assertThat(transaction)
            .describedAs("transaction required to create span")
            .isNotNull();

        span = transaction.createSpan();
        return span;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Span getSpan() {
        return span;
    }

}
