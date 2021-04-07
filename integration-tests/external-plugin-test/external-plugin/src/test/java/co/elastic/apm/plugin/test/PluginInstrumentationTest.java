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
package co.elastic.apm.plugin.test;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An example of how we can unit test our instrumentation, only as quick sanity check of functionality. It is not a full
 * test of the plugin, where it is loaded from a plugin directory and loaded with the proper class loader.
 * When running this test, the META-INF/services/co.elastic.apm.agent.sdk.ElasticApmInstrumentation file is loaded
 * from the system classpath and the instrumentation class is loaded as an internal plugin. In order to fully test
 * the external plugin, see `integration-tests/external-plugin-app`, which creates a webapp that is tested on all
 * Servlet containers in `integration-tests/application-server-integration-tests`.
 */
class PluginInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testTransactionCreation() {
        new TestClass().traceMe(false);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("traceMe");
        assertThat(reporter.getErrors()).isEmpty();
    }

    @Test
    void testSpanCreation() {
        Transaction transaction = tracer.startRootTransaction(null);
        Objects.requireNonNull(transaction).activate()
            .withName("Plugin test transaction")
            .withType("request");
        new TestClass().traceMe(false);
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("traceMe");
        assertThat(reporter.getErrors()).isEmpty();
    }

    @Test
    void testErrorCreation() {
        try {
            new TestClass().traceMe(true);
        } catch (IllegalStateException e) {
            // do nothing - expected
        }
        assertThat(reporter.getTransactions()).hasSize(1);
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo("traceMe");
        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(reporter.getFirstError().getTraceContext().getTransactionId()).isEqualTo(transaction.getTraceContext().getId());
    }
}
