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
package co.elastic.apm.agent.mdc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

class MdcActivationListenerTest extends AbstractInstrumentationTest {

    private LoggingConfiguration loggingConfiguration;
    private Boolean log4jMdcWorking;

    @BeforeEach
    void setUp() {
        org.apache.log4j.MDC.put("test", true);
        log4jMdcWorking = (Boolean) org.apache.log4j.MDC.get("test");

        forAllMdc(MdcImpl::clear);
        loggingConfiguration = config.getConfig(LoggingConfiguration.class);
    }

    @Test
    void testMdcIntegration() {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader()).withType("request").withName("test");
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
                grandchild.end();
                assertMdcIsSet(child);
            }
            child.end();
            assertMdcIsSet(transaction);
        }
        assertMdcIsEmpty();
        transaction.end();
    }

    @Test
    void testDisabledWhenInactive() {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        TracerInternalApiUtils.pauseTracer(tracer);
        Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader());
        assertThat(transaction).isNull();
        assertMdcIsEmpty();
    }

    @Test
    void testInactivationWhileMdcIsSet() {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader()).withType("request").withName("test");
        assertMdcIsEmpty();
        try (Scope scope = transaction.activateInScope()) {
            assertMdcIsSet(transaction);
            TracerInternalApiUtils.pauseTracer(tracer);
            Span child = transaction.createSpan();
            try (Scope childScope = child.activateInScope()) {
                assertMdcIsSet(transaction);
            }
            child.end();
            assertMdcIsSet(transaction);
        }
        assertMdcIsEmpty();
        transaction.end();
    }

    @Test
    void testMdcIntegrationTransactionScopeInDifferentThread() throws Exception {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader()).withType("request").withName("test");
        assertMdcIsEmpty();
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            assertMdcIsEmpty();
            try (Scope scope = transaction.activateInScope()) {
                assertMdcIsSet(transaction);
            }
            assertMdcIsEmpty();
            result.complete(true);
        });
        thread.start();
        assertThat(result.get(1000, TimeUnit.MILLISECONDS).booleanValue()).isTrue();
        assertMdcIsEmpty();
        thread.join();
        transaction.end();
    }

    @Test
    void testNoopWhenClassLoaderCantLoadMdc() throws Exception {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startRootTransaction(null).withType("request").withName("test");
        assertMdcIsEmpty();
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            assertMdcIsEmpty();
            try (Scope scope = transaction.activateInScope()) {
                assertMdcIsEmpty();
            }
            result.complete(true);
        });
        thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        thread.start();
        assertThat(result.get(1000, TimeUnit.MILLISECONDS).booleanValue()).isTrue();
        assertMdcIsEmpty();
        thread.join();
        transaction.end();
    }

    @Test
    void testWithNullContextClassLoader() throws Exception {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader()).withType("request").withName("test");
        assertMdcIsEmpty();
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            assertMdcIsEmpty();
            try (Scope scope = transaction.activateInScope()) {
                assertMdcIsSet(transaction);
            }
            assertMdcIsEmpty();
            result.complete(true);
        });
        thread.setContextClassLoader(null);
        thread.start();
        assertThat(result.get().booleanValue()).isTrue();
        assertMdcIsEmpty();
        thread.join();
        transaction.end();
    }

    @Test
    void testWithNullApplicationClassLoaderFallbackPlatformCL() throws Exception {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startRootTransaction(null).withType("request").withName("test");
        assertMdcIsEmpty();
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            assertMdcIsEmpty();
            try (Scope scope = transaction.activateInScope()) {
                // the platform CL can't load the MDC
                assertMdcIsEmpty();
            }
            assertMdcIsEmpty();
            result.complete(true);
        });
        thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        thread.start();
        assertThat(result.get().booleanValue()).isTrue();
        assertMdcIsEmpty();
        thread.join();
        transaction.end();
    }

    @Test
    void testWithNullApplicationClassLoaderFallbackCL() throws Exception {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startRootTransaction(null).withType("request").withName("test");
        assertMdcIsEmpty();
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            assertMdcIsEmpty();
            try (Scope scope = transaction.activateInScope()) {
                assertMdcIsSet(transaction);
            }
            assertMdcIsEmpty();
            result.complete(true);
        });
        thread.setContextClassLoader(getClass().getClassLoader());
        thread.start();
        assertThat(result.get().booleanValue()).isTrue();
        assertMdcIsEmpty();
        thread.join();
        transaction.end();
    }

    @Test
    void testMdcIntegrationContextScopeInDifferentThread() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            // initializes thread eagerly to avoid InheritableThreadLocal to inherit values to this thread
            executorService.submit(() -> {
            }).get();

            when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
            final Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader()).withType("request").withName("test");
            assertMdcIsEmpty();
            try (Scope scope = transaction.activateInScope()) {
                assertMdcIsSet(transaction);
                final Span child = transaction.createSpan();
                try (Scope childScope = child.activateInScope()) {
                    assertMdcIsSet(child);
                    executorService.submit(() -> {
                        assertMdcIsEmpty();
                        try (Scope otherThreadScope = child.activateInScope()) {
                            assertMdcIsSet(child);
                        }
                        assertMdcIsEmpty();
                    }).get();
                    assertMdcIsSet(child);
                }
                child.end();
                assertMdcIsSet(transaction);
            } finally {
                transaction.end();
            }
        } finally {
            executorService.shutdownNow();
        }
        assertMdcIsEmpty();
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

    private void assertMdcIsSet(AbstractSpan<?> span) {
        forAllMdc(mdc -> {
            assertThat(mdc.get("trace.id")).isEqualTo(span.getTraceContext().getTraceId().toString());
            assertThat(mdc.get("transaction.id")).isEqualTo(span.getTraceContext().getTransactionId().toString());
        });
    }

    private enum MdcImpl {
        Slf4j(
            MDC::get,
            MDC::clear),
        Log4j1(
            k -> (String) org.apache.log4j.MDC.get(k),
            org.apache.log4j.MDC::clear),
        Log4j2(
            ThreadContext::get,
            ThreadContext::clearAll),
        JbossLogging(
            k -> (String) org.jboss.logging.MDC.get(k),
            org.jboss.logging.MDC::clear);

        private final Function<String, String> get;
        private final Runnable clear;

        MdcImpl(Function<String, String> get, Runnable clear) {
            this.get = get;
            this.clear = clear;
        }

        @Nullable
        String get(String key) {
            return get.apply(key);
        }

        void clear() {
            clear.run();
        }
    }

    private void assertMdcIsEmpty() {
        forAllMdc(mdc -> {
            assertThat(mdc.get("trace.id")).isNull();
            assertThat(mdc.get("transaction.id")).isNull();
        });
    }

    private void forAllMdc(Consumer<MdcImpl> task) {
        Stream.of(MdcImpl.values())
            .filter(mdc -> mdc != MdcImpl.Log4j1 || log4jMdcWorking)
            .forEach(task);
    }

}
