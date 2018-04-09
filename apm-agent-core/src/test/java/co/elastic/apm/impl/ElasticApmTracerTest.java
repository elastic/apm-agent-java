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
package co.elastic.apm.impl;

import co.elastic.apm.MockReporter;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Tracer;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.context.LifecycleListener;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ElasticApmTracerTest {

    private ElasticApmTracer tracerImpl;
    private MockReporter reporter;
    private ConfigurationRegistry config;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        tracerImpl = ElasticApmTracer.builder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
    }

    @AfterEach
    void tearDown() {
        ElasticApmTracer.unregister();
    }

    @Test
    void testNotRegistered() {
        Tracer tracer = ElasticApm.get();
        try (co.elastic.apm.api.Transaction transaction = tracer.startTransaction()) {
            assertThat(transaction).isNotInstanceOf(Transaction.class);
        }
        assertThat(reporter.getTransactions()).isEmpty();
    }

    @Test
    void testRegister() {
        Tracer tracer = ElasticApm.get();
        tracerImpl.register();
        assertThat(ElasticApmTracer.get()).isSameAs(tracerImpl);
        try (co.elastic.apm.api.Transaction transaction = tracer.startTransaction()) {
            assertThat(transaction).isInstanceOf(Transaction.class);
        }
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    @Test
    void testThreadLocalStorage() {
        try (Transaction transaction = tracerImpl.startTransaction()) {
            assertThat(tracerImpl.currentTransaction()).isSameAs(transaction);
            try (Span span = tracerImpl.startSpan()) {
                assertThat(tracerImpl.currentSpan()).isSameAs(span);
                assertThat(transaction.getSpans()).containsExactly(span);
            }
            assertThat(tracerImpl.currentSpan()).isNull();
        }
        assertThat(tracerImpl.currentTransaction()).isNull();
    }

    @Test
    void testDisableStacktraces() {
        when(tracerImpl.getConfig(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(0);
        try (Transaction transaction = tracerImpl.startTransaction()) {
            try (Span span = tracerImpl.startSpan()) {
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isEmpty();
        }
    }

    @Test
    void testEnableStacktraces() throws InterruptedException {
        when(tracerImpl.getConfig(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(-1);
        try (Transaction transaction = tracerImpl.startTransaction()) {
            try (Span span = tracerImpl.startSpan()) {
                Thread.sleep(10);
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isNotEmpty();
        }
    }

    @Test
    void testDisableStacktracesForFastSpans() {
        when(tracerImpl.getConfig(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(100);
        try (Transaction transaction = tracerImpl.startTransaction()) {
            try (Span span = tracerImpl.startSpan()) {
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isEmpty();
        }
    }

    @Test
    void testEnableStacktracesForSlowSpans() throws InterruptedException {
        when(tracerImpl.getConfig(StacktraceConfiguration.class).getSpanFramesMinDurationMs()).thenReturn(1);
        try (Transaction transaction = tracerImpl.startTransaction()) {
            try (Span span = tracerImpl.startSpan()) {
                Thread.sleep(10);
            }
            assertThat(transaction.getSpans().get(0).getStacktrace()).isNotEmpty();
        }
    }

    @Test
    void testRecordException() {
        tracerImpl.captureException(new Exception("test"));
        assertThat(reporter.getErrors()).hasSize(1);
        ErrorCapture error = reporter.getFirstError();
        assertThat(error.getId().isEmpty()).isFalse();
        assertThat(error.getException().getStacktrace()).isNotEmpty();
        assertThat(error.getException().getMessage()).isEqualTo("test");
        assertThat(error.getException().getType()).isEqualTo(Exception.class.getName());
        assertThat(error.getTransaction().getId().isEmpty()).isTrue();
    }

    @Test
    void testRecordExceptionWithTrace() {
        try (Transaction transaction = tracerImpl.startTransaction()) {
            transaction.getContext().getRequest().addHeader("foo", "bar");
            tracerImpl.captureException(new Exception("test"));
            assertThat(reporter.getErrors()).hasSize(1);
            ErrorCapture error = reporter.getFirstError();
            assertThat(error.getTransaction().getId()).isEqualTo(transaction.getId());
            assertThat(error.getContext().getRequest().getHeaders()).containsEntry("foo", "bar");
        }
    }

    @Test
    void testEnableDropSpans() {
        when(tracerImpl.getConfig(CoreConfiguration.class).getTransactionMaxSpans()).thenReturn(1);
        try (Transaction transaction = tracerImpl.startTransaction()) {
            try (Span span = tracerImpl.startSpan()) {
                assertThat(span.isSampled()).isTrue();
            }
            try (Span span = tracerImpl.startSpan()) {
                assertThat(span.isSampled()).isFalse();
            }
            assertThat(transaction.isSampled()).isTrue();
            assertThat(transaction.getSpans()).hasSize(1);
            assertThat(transaction.getSpanCount().getDropped().getTotal()).isEqualTo(1);
        }
    }

    @Test
    void testDisable() {
        when(config.getConfig(CoreConfiguration.class).isActive()).thenReturn(false);
        try (Transaction transaction = tracerImpl.startTransaction()) {
            assertThat(tracerImpl.currentTransaction()).isSameAs(transaction);
            assertThat(transaction.isSampled()).isFalse();
            try (Span span = tracerImpl.startSpan()) {
                assertThat(tracerImpl.currentSpan()).isSameAs(span);
                assertThat(transaction.getSpans()).isEmpty();
                assertThat(span.isSampled()).isFalse();
            }
            assertThat(tracerImpl.currentSpan()).isNull();
        }
        assertThat(tracerImpl.currentTransaction()).isNull();
        assertThat(reporter.getTransactions()).isEmpty();
    }

    @Test
    void testDisableMidTransaction() {
        Transaction transaction = tracerImpl.startTransaction();
        try (transaction) {
            assertThat(tracerImpl.currentTransaction()).isSameAs(transaction);
            try (Span span = tracerImpl.startSpan()) {
                when(config.getConfig(CoreConfiguration.class).isActive()).thenReturn(false);
                span.withName("test");
                assertThat(span.getName()).isEqualTo("test");
                assertThat(tracerImpl.currentSpan()).isSameAs(span);
                assertThat(transaction.getSpans()).containsExactly(span);
            }
            try (Span span = tracerImpl.startSpan()) {
                when(config.getConfig(CoreConfiguration.class).isActive()).thenReturn(false);
                span.withName("test2");
                assertThat(span.getName()).isEqualTo("test2");
                assertThat(tracerImpl.currentSpan()).isSameAs(span);
                assertThat(transaction.getSpans()).contains(span);
            }
            assertThat(tracerImpl.currentSpan()).isNull();
        }
        assertThat(tracerImpl.currentTransaction()).isNull();
        assertThat(transaction.getSpans()).hasSize(2);
        assertThat(reporter.getFirstTransaction()).isSameAs(transaction);

    }

    @Test
    void testSamplingNone() throws IOException {
        config.getConfig(CoreConfiguration.class).getSampleRate().update(0.0, SpyConfiguration.CONFIG_SOURCE_NAME);
        try (Transaction transaction = tracerImpl.startTransaction()) {
            transaction.setUser("1", "jon.doe@example.com", "jondoe");
            try (Span span = tracerImpl.startSpan()) {
            }
        }
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getSpans()).hasSize(0);
        assertThat(reporter.getFirstTransaction().getContext().getUser().getEmail()).isNull();
    }

    @Test
    void testLifecycleListener() {
        final AtomicBoolean startCalled = new AtomicBoolean();
        final AtomicBoolean stopCalled = new AtomicBoolean();
        final ElasticApmTracer tracer = ElasticApmTracer.builder()
            .configurationRegistry(config)
            .reporter(reporter)
            .lifecycleListeners(Collections.singletonList(new LifecycleListener() {
                @Override
                public void start(ElasticApmTracer tracer) {
                    startCalled.set(true);
                }

                @Override
                public void stop() {
                    stopCalled.set(true);
                }
            }))
            .build();
        assertThat(startCalled).isTrue();
        assertThat(stopCalled).isFalse();

        tracer.stop();
        assertThat(stopCalled).isTrue();
    }

    @Test
    void testSpanWithoutTransaction() {
        try (Span span = tracerImpl.startSpan()) {
            assertThat(span.isSampled()).isFalse();
        }

    }
}
