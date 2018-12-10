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
package co.elastic.apm.plugin.api;

import co.elastic.apm.AbstractInstrumentationTest;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Scope;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpanInstrumentationTest extends AbstractInstrumentationTest {

    private Span span;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = ElasticApm.startTransaction();
        span = transaction.createSpan();
    }

    @Test
    void testSetName() {
        span.setName("foo");
        endSpan();
        assertThat(reporter.getFirstSpan().getName().toString()).isEqualTo("foo");
    }

    @Test
    void testSetType() {
        span.setType("foo");
        endSpan();
        assertThat(reporter.getFirstSpan().getType()).isEqualTo("foo");
    }

    private void endSpan() {
        span.end();
        transaction.end();
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    @Test
    void testScope() {
        assertThat(ElasticApm.currentSpan().getId()).isNotEqualTo(span.getId());
        try (final Scope scope = span.activate()) {
            assertThat(ElasticApm.currentSpan().getId()).isEqualTo(span.getId());
            ElasticApm.currentSpan().createSpan().end();
        }
        span.end();
        transaction.end();
        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans().get(0).isChildOf(reporter.getSpans().get(1))).isTrue();
        assertThat(reporter.getSpans().get(1).isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    @Test
    void testSampled() {
        assertThat(ElasticApm.currentSpan().isSampled()).isFalse();
        assertThat(ElasticApm.currentTransaction().isSampled()).isFalse();
        final Transaction transaction = ElasticApm.startTransaction();
        assertThat(transaction.isSampled()).isTrue();
        assertThat(transaction.createSpan().isSampled()).isTrue();
    }
}
