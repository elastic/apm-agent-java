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
package co.elastic.apm.agent.plugin;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.plugin.test.TestClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An example of how we can unit test our instrumentation, only as quick sanity check of functionality. It is not a full
 * test of the plugin, where it is loaded from a plugin directory and loaded with the proper class loader.
 * When running this test, the META-INF/services/co.elastic.apm.agent.sdk.ElasticApmInstrumentation file is loaded
 * from the system classpath and the instrumentation class is loaded as an internal plugin. In order to fully test
 * the external plugin, see `integration-tests/external-plugin-app` and `integration-tests/external-plugin-jakarta-app`
 * that create webapps that are tested on all Servlet containers in `integration-tests/application-server-integration-tests`.
 * <br/>
 * Implementation note: within this test, due to not testing a packaged external plugin, the
 * {@code co.elastic.apm.agent.} package prefix is required for the instrumentation class. When plugin is loaded
 * from a real external plugin this constraint does not apply.
 */
class PluginInstrumentationTest extends AbstractInstrumentationTest {

    @BeforeEach
    void disableObjectRecyclingAssertion() {
        disableRecyclingValidation();
    }

    @Test
    void testTransactionCreation() {
        new TestClass().traceMe(false);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("TestClass#traceMe");
        assertThat(reporter.getErrors()).isEmpty();
    }

    @Test
    void testSpanCreation() {
        // using custom span type/sub-type not part of shared spec
        reporter.disableCheckStrictSpanType();

        Transaction transaction = tracer.startRootTransaction(null);
        Objects.requireNonNull(transaction).activate()
            .withName("Plugin test transaction")
            .withType("request");
        new TestClass().traceMe(false);
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        Span span = reporter.getFirstSpan();
        assertThat(span.getNameAsString()).isEqualTo("traceMe");
        assertThat(span.getType()).isEqualTo("plugin");
        assertThat(span.getSubtype()).isEqualTo("external");
        assertThat(span.getAction()).isEqualTo("trace");
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
        assertThat(transaction.getNameAsString()).isEqualTo("TestClass#traceMe");
        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(reporter.getFirstError().getTraceContext().getTransactionId()).isEqualTo(transaction.getTraceContext().getId());
    }
}
