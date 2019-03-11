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
package co.elastic.apm.agent.slf4j;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

class Slf4JMdcActivationListenerTest extends AbstractInstrumentationTest {

    private LoggingConfiguration loggingConfiguration;

    @BeforeEach
    void setUp() {
        MDC.clear();
        loggingConfiguration = config.getConfig(LoggingConfiguration.class);
    }

    @Test
    void testMdcIntegration() {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).withType("request").withName("test");
        assertMdcIsEmpty();
        try (Scope scope = transaction.activateInScope()) {
            assertMdcIsSet(transaction);
            Span child = transaction.createSpan();
            try (Scope childScope = child.activateInScope()) {
                assertMdcIsSet(child);
                Span grandchild = child.createSpan();
                try (Scope grandchildScope = grandchild.activateInScope()) {
                    assertMdcIsSet(grandchild);
                }
                assertMdcIsSet(child);
            }
            assertMdcIsSet(transaction);
        }
        assertMdcIsEmpty();
        transaction.end();
    }

    @Test
    void testMdcIntegrationTransactionScopeInDifferentThread() throws Exception {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).withType("request").withName("test");
        assertMdcIsEmpty();
        Thread thread = new Thread(() -> {
            assertMdcIsEmpty();
            try (Scope scope = transaction.activateInScope()) {
                assertMdcIsSet(transaction);
            }
            assertMdcIsEmpty();
        });
        thread.start();
        assertMdcIsEmpty();
        thread.join();
        transaction.end();
    }

    @Test
    void testMdcIntegrationContextScopeInDifferentThread() throws Exception {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).withType("request").withName("test");
        assertMdcIsEmpty();
        try (Scope scope = transaction.activateInScope()) {
            assertMdcIsSet(transaction);
            final Span child = transaction.createSpan();
            try (Scope childScope = child.activateInScope()) {
                assertMdcIsSet(child);
                Thread thread = new Thread(() -> {
                    assertMdcIsEmpty();
                    try (Scope otherThreadScope = child.getTraceContext().activateInScope()) {
                        assertMdcIsSet(child);
                    }
                    assertMdcIsEmpty();
                });
                thread.start();
                thread.join();
                assertMdcIsSet(child);
            }
            assertMdcIsSet(transaction);
        }
        assertMdcIsEmpty();
        transaction.end();
    }

    @Test
    void testDisablingAtRuntimeNotPossible() throws IOException {
        assertThat(loggingConfiguration.isLogCorrelationEnabled()).isFalse();
        config.save("enable_log_correlation", "true", SpyConfiguration.CONFIG_SOURCE_NAME);
        assertThat(loggingConfiguration.isLogCorrelationEnabled()).isTrue();
        assertThatCode(() -> config.save("enable_log_correlation", "false", SpyConfiguration.CONFIG_SOURCE_NAME))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Disabling the log correlation at runtime is not possible.");
        assertThat(loggingConfiguration.isLogCorrelationEnabled()).isTrue();
    }

    private void assertMdcIsSet(AbstractSpan span) {
        assertThat(MDC.get("trace.id")).isEqualTo(span.getTraceContext().getTraceId().toString());
        assertThat(MDC.get("transaction.id")).isEqualTo(span.getTraceContext().getTransactionId().toString());
        assertThat(MDC.get("span.id")).isEqualTo(span.getTraceContext().getId().toString());
    }

    private void assertMdcIsEmpty() {
        assertThat(MDC.get("trace.id")).isNull();
        assertThat(MDC.get("transaction.id")).isNull();
        assertThat(MDC.get("span.id")).isNull();
    }

}
