/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.plugin.api;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionInstrumentationTest extends AbstractInstrumentationTest {

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = ElasticApm.startTransaction();
        transaction.setType("default");
    }


    @Test
    void testSetName() {
        transaction.setName("foo");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getName().toString()).isEqualTo("foo");
    }

    @Test
    void testSetType() {
        transaction.setType("foo");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getType()).isEqualTo("foo");
    }

    @Test
    void testAddTag() {
        transaction.addTag("foo", "bar");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getContext().getTags()).containsEntry("foo", "bar");
    }

    @Test
    void testSetUser() {
        transaction.setUser("foo", "bar", "baz");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getContext().getUser().getId()).isEqualTo("foo");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getEmail()).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getUsername()).isEqualTo("baz");
    }

    @Test
    void testResult() {
        transaction.setResult("foo");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getResult()).isEqualTo("foo");
    }

    @Test
    void testChaining() {
        transaction.setType("foo").setName("foo").addTag("foo", "bar").setUser("foo", "bar", "baz").setResult("foo");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getName().toString()).isEqualTo("foo");
        assertThat(reporter.getFirstTransaction().getType()).isEqualTo("foo");
        assertThat(reporter.getFirstTransaction().getContext().getTags()).containsEntry("foo", "bar");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getId()).isEqualTo("foo");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getEmail()).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getUsername()).isEqualTo("baz");
        assertThat(reporter.getFirstTransaction().getResult()).isEqualTo("foo");
    }

    @Test
    public void startSpan() throws Exception {
        Span span = transaction.startSpan("foo", null, null);
        span.setName("bar");
        Span child = span.startSpan("foo2", null, null);
        child.setName("bar2");
        Span span3 = transaction.startSpan("foo3", null, null);
        span3.setName("bar3");
        span3.end();
        child.end();
        span.end();
        endTransaction();
        assertThat(reporter.getSpans()).hasSize(3);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstSpan().getType()).isEqualTo("foo3");
        assertThat(reporter.getFirstSpan().getName().toString()).isEqualTo("bar3");
        assertThat(reporter.getSpans().get(1).getType()).isEqualTo("foo2");
        assertThat(reporter.getSpans().get(1).getName().toString()).isEqualTo("bar2");
        assertThat(reporter.getSpans().get(1).getTraceContext().getParentId()).isEqualTo(reporter.getSpans().get(2).getTraceContext().getId());
        assertThat(reporter.getFirstSpan().getTraceContext().getParentId()).isEqualTo(reporter.getFirstTransaction().getTraceContext().getId());
    }
    
    private void endTransaction() {
        transaction.end();
        assertThat(reporter.getTransactions()).hasSize(1);
    }
}
