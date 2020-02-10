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
package co.elastic.apm.agent.mdc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

class MdcActivationListenerTest extends AbstractInstrumentationTest {

    private LoggingConfiguration loggingConfiguration;
    private CoreConfiguration coreConfiguration;
    private Boolean log4jMdcWorking;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @BeforeEach
    void setUp() throws Exception {
        org.apache.log4j.MDC.put("test", true);
        log4jMdcWorking = (Boolean) org.apache.log4j.MDC.get("test");
        MDC.clear();
        org.apache.log4j.MDC.clear();
        ThreadContext.clearAll();
        loggingConfiguration = config.getConfig(LoggingConfiguration.class);
        coreConfiguration = config.getConfig(CoreConfiguration.class);
        // initializes thread eagerly to avoid InheritableThreadLocal to inherit values to this thread
        executorService.submit(() -> {}).get();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
    }

    @Test
    void testMdcIntegration() {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, getClass().getClassLoader()).withType("request").withName("test");
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
    void testDisabledWhenInactive() {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        when(coreConfiguration.isActive()).thenReturn(false);
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, getClass().getClassLoader()).withType("request").withName("test");
        assertMdcIsEmpty();
        try (Scope scope = transaction.activateInScope()) {
            assertMdcIsEmpty();
            Span child = transaction.createSpan();
            try (Scope childScope = child.activateInScope()) {
                assertMdcIsEmpty();
            }
        }
        transaction.end();
        when(coreConfiguration.isActive()).thenReturn(true);
    }

    @Test
    void testInactivationWhileMdcIsSet() {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, getClass().getClassLoader()).withType("request").withName("test");
        assertMdcIsEmpty();
        try (Scope scope = transaction.activateInScope()) {
            assertMdcIsSet(transaction);
            when(coreConfiguration.isActive()).thenReturn(false);
            Span child = transaction.createSpan();
            try (Scope childScope = child.activateInScope()) {
                assertMdcIsSet(transaction);
            }
            assertMdcIsSet(transaction);
        }
        assertMdcIsEmpty();
        transaction.end();
        when(coreConfiguration.isActive()).thenReturn(true);
    }

    @Test
    void testMdcIntegrationTransactionScopeInDifferentThread() throws Exception {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, getClass().getClassLoader()).withType("request").withName("test");
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
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).withType("request").withName("test");
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
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, getClass().getClassLoader()).withType("request").withName("test");
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
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).withType("request").withName("test");
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
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).withType("request").withName("test");
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
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, getClass().getClassLoader()).withType("request").withName("test");
        assertMdcIsEmpty();
        try (Scope scope = transaction.activateInScope()) {
            assertMdcIsSet(transaction);
            final Span child = transaction.createSpan();
            try (Scope childScope = child.activateInScope()) {
                assertMdcIsSet(child);
                executorService.submit(() -> {
                    assertMdcIsEmpty();
                    try (Scope otherThreadScope = child.getTraceContext().activateInScope()) {
                        assertMdcIsSet(child);
                    }
                    assertMdcIsEmpty();
                }).get();
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
        assertThat(ThreadContext.get("trace.id")).isEqualTo(span.getTraceContext().getTraceId().toString());
        assertThat(ThreadContext.get("transaction.id")).isEqualTo(span.getTraceContext().getTransactionId().toString());
        if (log4jMdcWorking == Boolean.TRUE) {
            assertThat(org.apache.log4j.MDC.get("trace.id")).isEqualTo(span.getTraceContext().getTraceId().toString());
            assertThat(org.apache.log4j.MDC.get("transaction.id")).isEqualTo(span.getTraceContext().getTransactionId().toString());
        }
    }

    private void assertMdcIsEmpty() {
        assertThat(MDC.get("trace.id")).isNull();
        assertThat(MDC.get("transaction.id")).isNull();
        assertThat(ThreadContext.get("trace.id")).isNull();
        assertThat(ThreadContext.get("transaction.id")).isNull();
        if (log4jMdcWorking == Boolean.TRUE) {
            assertThat(org.apache.log4j.MDC.get("transaction.id")).isNull();
            assertThat(org.apache.log4j.MDC.get("trace.id")).isNull();
        }
    }

}
