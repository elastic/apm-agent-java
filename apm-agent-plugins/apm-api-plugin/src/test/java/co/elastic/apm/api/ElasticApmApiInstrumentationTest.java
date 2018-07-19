/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.api;

import co.elastic.apm.AbstractInstrumentationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticApmApiInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testCreateTransaction() {
        assertThat(ElasticApm.startTransaction()).isNotSameAs(NoopTransaction.INSTANCE);
        assertThat(ElasticApm.currentTransaction()).isSameAs(NoopTransaction.INSTANCE);
    }

    @Test
    void testNoCurrentTransaction() {
        assertThat(ElasticApm.currentTransaction()).isSameAs(NoopTransaction.INSTANCE);
    }

    @Test
    void testCreateSpan() {
        assertThat(ElasticApm.startTransaction().createSpan()).isNotSameAs(NoopSpan.INSTANCE);
        assertThat(ElasticApm.currentSpan()).isSameAs(NoopSpan.INSTANCE);
    }

    @Test
    void testNoCurrentSpan() {
        assertThat(ElasticApm.currentSpan()).isSameAs(NoopSpan.INSTANCE);
    }

    @Test
    void testCaptureException() {
        ElasticApm.captureException(new RuntimeException("Bazinga"));
        assertThat(reporter.getErrors()).hasSize(1);
    }

    @Test
    void testCreateChildSpanOfCurrentTransaction() {
        final co.elastic.apm.impl.transaction.Transaction  transaction = tracer.startTransaction().withType("request").withName("transaction").activate();
        final Span span = ElasticApm.currentSpan().createSpan();
        span.setName("span");
        span.setType("db");
        span.end();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getTraceContext().getParentId()).isEqualTo(reporter.getFirstTransaction().getTraceContext().getId());
    }

    @Test
    // https://github.com/elastic/apm-agent-java/issues/132
    void testAutomaticAndManualTransactions() {
        final co.elastic.apm.impl.transaction.Transaction  transaction = tracer.startTransaction().withType("request").withName("transaction").activate();
        final Transaction manualTransaction = ElasticApm.startTransaction();
        manualTransaction.setName("manual transaction");
        manualTransaction.setType("request");
        manualTransaction.end();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(2);
    }
}
