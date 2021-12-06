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

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

public class SpecTracerState {

    private final ElasticApmTracer tracer;

    private Transaction transaction;
    private Span span;

    public SpecTracerState() {
        tracer = MockTracer.createRealTracer();
    }

    public ElasticApmTracer getTracer() {
        return tracer;
    }

    public void startRootTransactionIfRequired() {
        if (transaction == null) {
            startRootTransaction();
        }
    }

    public Transaction startRootTransaction() { // TODO : rename to startRootTransaction
        assertThat(tracer.getActiveContext())
            .describedAs("context should be empty when starting transaction")
            .isNull();

        transaction = tracer.startRootTransaction(getClass().getClassLoader());
        return transaction;
    }

    public Span startSpan() {
        Transaction transaction = getTransaction();
        assertThat(transaction)
            .describedAs("active transaction required to create span")
            .isNotNull();

        span = transaction.createSpan();
        return span;
    }

    @Nullable
    public Transaction getTransaction() {
        return transaction;
    }

    @Nullable
    public Span getSpan() {
        return span;
    }

}
