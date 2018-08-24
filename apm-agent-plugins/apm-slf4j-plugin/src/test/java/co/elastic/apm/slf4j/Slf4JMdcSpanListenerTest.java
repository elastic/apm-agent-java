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
package co.elastic.apm.slf4j;

import co.elastic.apm.AbstractInstrumentationTest;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.Scope;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.logging.LoggingConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

class Slf4JMdcSpanListenerTest extends AbstractInstrumentationTest {

    private LoggingConfiguration loggingConfiguration;

    @BeforeEach
    void setUp() {
        MDC.clear();
        loggingConfiguration = config.getConfig(LoggingConfiguration.class);
    }

    @Test
    void testMdcIntegration() {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startTransaction().withType("request").withName("test");
        assertMdcIsEmpty();
        try (Scope scope = transaction.activateInScope()) {
            assertMdcIsSet(transaction);
        }
        assertMdcIsEmpty();
        transaction.end();
    }

    @Test
    void testMdcIntegrationScopeInDifferentThread() throws Exception {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startTransaction().withType("request").withName("test");
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
    void testDisablingAtRuntimeNotPossible() throws IOException {
        assertThat(loggingConfiguration.isLogCorrelationEnabled()).isFalse();
        config.save("enable_log_correlation", "true", SpyConfiguration.CONFIG_SOURCE_NAME);
        assertThat(loggingConfiguration.isLogCorrelationEnabled()).isTrue();
        assertThatCode(() -> config.save("enable_log_correlation", "false", SpyConfiguration.CONFIG_SOURCE_NAME))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Disabling the log correlation at runtime is not possible.");
        assertThat(loggingConfiguration.isLogCorrelationEnabled()).isTrue();
    }

    private void assertMdcIsSet(Transaction transaction) {
        assertThat(MDC.get("traceId")).isEqualTo(transaction.getTraceContext().getTraceId().toString());
        assertThat(MDC.get("spanId")).isEqualTo(transaction.getTraceContext().getId().toString());
    }

    private void assertMdcIsEmpty() {
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }

}
