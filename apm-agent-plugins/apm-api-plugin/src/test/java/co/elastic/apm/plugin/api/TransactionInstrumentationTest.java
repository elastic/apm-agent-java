/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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

    private void endTransaction() {
        transaction.end();
        assertThat(reporter.getTransactions()).hasSize(1);
    }
}
