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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServiceNameUtil;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.source.ConfigSources;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ElasticApmTracerTest {

    private ElasticApmTracer tracerImpl;
    private MockReporter reporter;
    private ConfigurationRegistry config;

    private TestObjectPoolFactory objectPoolFactory;

    @BeforeEach
    void setUp() {
        objectPoolFactory = new TestObjectPoolFactory();
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        tracerImpl = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .withObjectPoolFactory(objectPoolFactory)
            .buildAndStart();
    }

    @AfterEach
    void cleanupAndCheck() {
        reporter.assertRecycledAfterDecrementingReferences();
        objectPoolFactory.checkAllPooledObjectsHaveBeenRecycled();
        tracerImpl.resetServiceNameOverrides();
    }

    @Test
    void testThreadLocalStorage() {
        Transaction transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            assertThat(tracerImpl.currentTransaction()).isSameAs(transaction);
            Span span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                assertThat(tracerImpl.currentTransaction()).isSameAs(transaction);
                assertThat(tracerImpl.getActive()).isSameAs(span);
                assertThat(span.isChildOf(transaction)).isTrue();
                span.end();
            }
            assertThat(tracerImpl.getActive()).isEqualTo(transaction);
            transaction.end();
        }
        assertThat(tracerImpl.currentTransaction()).isNull();
    }

    @Test
    void testNestedSpan() {
        Transaction transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            assertThat(tracerImpl.currentTransaction()).isSameAs(transaction);
            Span span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                assertThat(tracerImpl.getActive()).isSameAs(span);
                assertThat(span.isChildOf(transaction)).isTrue();
                Span nestedSpan = tracerImpl.getActive().createSpan();
                try (Scope nestedSpanScope = nestedSpan.activateInScope()) {
                    assertThat(tracerImpl.getActive()).isSameAs(nestedSpan);
                    assertThat(nestedSpan.isChildOf(span)).isTrue();
                    nestedSpan.end();
                }
                span.end();
            }
            assertThat(tracerImpl.getActive()).isEqualTo(transaction);
            transaction.end();
        }
        assertThat(tracerImpl.currentTransaction()).isNull();
        assertThat(reporter.getSpans()).hasSize(2);
    }

    @Test
    void testDisableStacktraces() {
        when(tracerImpl.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs()).thenReturn(-1L);

        Transaction transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            Span span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                span.end();
            }
            transaction.end();
        }
        assertThat(reporter.getFirstSpan().getStacktrace()).isNull();
    }

    @Test
    void testEnableStacktraces() throws InterruptedException {
        when(tracerImpl.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs()).thenReturn(0L);
        Transaction transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            Span span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                Thread.sleep(10);
                span.end();
            }
            transaction.end();
        }
        assertThat(reporter.getFirstSpan().getStacktrace()).isNotNull();
    }

    @Test
    void testDisableStacktracesForFastSpans() {
        when(tracerImpl.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs()).thenReturn(100L);
        Transaction transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            Span span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                span.end();
            }
            transaction.end();
        }
        assertThat(reporter.getFirstSpan().getStacktrace()).isNull();

    }

    @Test
    void testEnableStacktracesForSlowSpans() throws InterruptedException {
        when(tracerImpl.getConfig(StacktraceConfiguration.class).getSpanStackTraceMinDurationMs()).thenReturn(1L);
        Transaction transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            Span span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                Thread.sleep(10);
                span.end();
            }
            transaction.end();
        }
        assertThat(reporter.getFirstSpan().getStacktrace()).isNotNull();
    }

    @Nullable
    private Transaction startTestRootTransaction() {
        return tracerImpl.startRootTransaction(getClass().getClassLoader());
    }

    @Test
    void testRecordException() {
        tracerImpl.captureAndReportException(new Exception("test"), getClass().getClassLoader());
        assertThat(reporter.getErrors()).hasSize(1);
        ErrorCapture error = reporter.getFirstError();
        assertThat(error.getException()).isNotNull();
        assertThat(error.getTraceContext().hasContent()).isTrue();
        assertThat(error.getTraceContext().getTraceId().isEmpty()).isTrue();
        assertThat(error.getTransactionInfo().isSampled()).isFalse();
    }

    @Test
    void testDoesNotRecordIgnoredExceptions() {
        List<WildcardMatcher> wildcardList = Stream.of(
            "co.elastic.apm.agent.impl.ElasticApmTracerTest$DummyException1",
            "*DummyException2")
            .map(WildcardMatcher::valueOf)
            .collect(Collectors.toList());

        when(config.getConfig(CoreConfiguration.class).getIgnoreExceptions())
            .thenReturn(wildcardList);

        tracerImpl.captureAndReportException(new DummyException1(), getClass().getClassLoader());
        tracerImpl.captureAndReportException(new DummyException2(), getClass().getClassLoader());
        assertThat(reporter.getErrors()).isEmpty();
    }

    private static class DummyException1 extends Exception {
        DummyException1() {
        }
    }

    private static class DummyException2 extends Exception {
        DummyException2() {
        }
    }

    @Test
    void testRecordExceptionWithTraceSampled() {
        innerRecordExceptionWithTrace(true);
    }

    @Test
    void testRecordExceptionWithTraceNotSampled() {
        innerRecordExceptionWithTrace(false);
    }

    private void innerRecordExceptionWithTrace(boolean sampled) {
        Transaction transaction = tracerImpl.startRootTransaction(ConstantSampler.of(sampled), -1, null);
        transaction.withType("request");
        try (Scope scope = transaction.activateInScope()) {
            transaction.getContext().getRequest()
                .addHeader("foo", "bar")
                .withMethod("GET")
                .getUrl()
                .withPathname("/foo");
            tracerImpl.currentTransaction().captureException(new Exception("from transaction"));

            Span span = transaction.createSpan().activate();
            span.captureException(new Exception("from span"));

            List<ErrorCapture> errors = reporter.getErrors();
            assertThat(errors).hasSize(2);

            // 1st one is from transaction
            validateError(errors.get(0), transaction, sampled, transaction);
            assertThat(errors.get(0).getContext().getRequest().getHeaders().get("foo")).isEqualTo("bar");

            // second one is the one from span
            validateError(errors.get(1), span, sampled, transaction);

            span.deactivate().end();
        }
        transaction.end();
    }

    private ErrorCapture validateSingleError(AbstractSpan<?> span, boolean sampled, Transaction correspondingTransaction) {
        assertThat(reporter.getErrors()).hasSize(1);
        return validateError(reporter.getFirstError(), span, sampled, correspondingTransaction);
    }

    private ErrorCapture validateError(ErrorCapture error, AbstractSpan<?> span, boolean sampled, Transaction transaction) {
        assertThat(error.getTraceContext().isChildOf(span.getTraceContext()))
            .describedAs("error trace context [%s] should be a child of span trace context [%s]", error.getTraceContext(), span.getTraceContext())
            .isTrue();
        assertThat(error.getTransactionInfo().isSampled()).isEqualTo(sampled);
        if (!transaction.getNameAsString().isEmpty()) {
            assertThat(error.getTransactionInfo().getName()).isEqualTo(transaction.getNameAsString());
        }
        assertThat(error.getTransactionInfo().getType()).isEqualTo(transaction.getType());
        return error;
    }

    @Test
    void testEnableDropSpans() {
        when(tracerImpl.getConfig(CoreConfiguration.class).getTransactionMaxSpans()).thenReturn(1);
        Transaction transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            Span span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                assertThat(span.isSampled()).isTrue();
                span.end();
            }
            Span span2 = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span2.activateInScope()) {
                assertThat(span2.isSampled()).isFalse();
                span2.end();
            }
            transaction.end();
        }
        assertThat(reporter.getFirstTransaction().isSampled()).isTrue();
        assertThat(reporter.getFirstTransaction().getSpanCount().getDropped()).hasValue(1);
        assertThat(reporter.getFirstTransaction().getSpanCount().getReported()).hasValue(1);
        assertThat(reporter.getFirstTransaction().getSpanCount().getTotal()).hasValue(2);
        assertThat(reporter.getSpans()).hasSize(1);
    }

    @Test
    void testPause() {
        tracerImpl.pause();
        assertThat(startTestRootTransaction()).isNull();
    }

    @Test
    void testPauseMidTransaction() {
        Transaction transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            assertThat(tracerImpl.currentTransaction()).isSameAs(transaction);
            tracerImpl.pause();
            Span span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                span.withName("test");
                assertThat(span.getNameAsString()).isEqualTo("test");
                assertThat(tracerImpl.getActive()).isSameAs(span);
                assertThat(span.isChildOf(transaction)).isTrue();
                span.end();
            }
            Span span2 = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span2.activateInScope()) {
                span2.withName("test2");
                assertThat(span2.getNameAsString()).isEqualTo("test2");
                assertThat(tracerImpl.getActive()).isSameAs(span2);
                assertThat(span2.isChildOf(transaction)).isTrue();
                span2.end();
            }
            assertThat(tracerImpl.getActive()).isEqualTo(transaction);
            transaction.end();
        }
        assertThat(tracerImpl.currentTransaction()).isNull();
        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(reporter.getFirstTransaction()).isSameAs(transaction);
    }

    @Test
    void testSamplingNone() throws IOException {
        config.getConfig(CoreConfiguration.class).getSampleRate().update(0.0, SpyConfiguration.CONFIG_SOURCE_NAME);
        Transaction transaction = startTestRootTransaction().withType("request");

        try (Scope scope = transaction.activateInScope()) {
            transaction.setUser("1", "jon.doe@example.com", "jondoe", "domain");
            Span span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                span.end();
            }
            transaction.end();
        }
        // we do report non-sampled transactions (without the context)
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(0);
        assertThat(reporter.getFirstTransaction().getContext().getUser().getEmail()).isNull();
        assertThat(reporter.getFirstTransaction().getType()).isEqualTo("request");
    }

    @Test
    void testTransactionWithParentReference() {
        final Map<String, String> headerMap = Map.of(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final Transaction transaction = tracerImpl.startChildTransaction(headerMap, TextHeaderMapAccessor.INSTANCE, ConstantSampler.of(false), 0, null);
        // the traced flag in the header overrides the sampler
        assertThat(transaction.isSampled()).isTrue();
        assertThat(transaction.getTraceContext().getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(transaction.getTraceContext().getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        transaction.end(1);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(0);
    }

    @Test
    void testTimestamps() {
        final Transaction transaction = tracerImpl.startChildTransaction(new HashMap<>(), TextHeaderMapAccessor.INSTANCE, ConstantSampler.of(true), 0, null);
        final Span span = transaction.createSpan(10);
        span.end(20);

        transaction.end(30);

        assertThat(transaction.getTimestamp()).isEqualTo(0);
        assertThat(transaction.getDuration()).isEqualTo(30);
        assertThat(span.getTimestamp()).isEqualTo(10);
        assertThat(span.getDuration()).isEqualTo(10);
    }

    @Test
    void testStartSpanAfterTransactionHasEnded() {
        final Transaction transaction = startTestRootTransaction();
        assertThat(transaction).isNotNull();
        transaction.incrementReferences();
        transaction.end();


        try (Scope transactionScope = transaction.activateInScope()) {
            assertThat(tracerImpl.getActive()).isEqualTo(transaction);
            final Span span = tracerImpl.startSpan(TraceContext.fromActive(), tracerImpl);
            assertThat(span).isNotNull();
            try (Scope scope = span.activateInScope()) {
                assertThat(tracerImpl.currentTransaction()).isNotNull();
                assertThat(tracerImpl.getActive()).isSameAs(span);
            } finally {
                span.end();
            }
        }
        assertThat(tracerImpl.getActive()).isNull();
        transaction.decrementReferences();
        reporter.assertRecycledAfterDecrementingReferences();
    }

    @Test
    void testActivateDeactivateTwice() {
        final Transaction transaction = startTestRootTransaction();
        assertThat(tracerImpl.currentTransaction()).isNull();
        tracerImpl.activate(transaction);
        assertThat(tracerImpl.currentTransaction()).isEqualTo(transaction);
        tracerImpl.activate(transaction);
        assertThat(tracerImpl.currentTransaction()).isEqualTo(transaction);
        assertThat(tracerImpl.getActive()).isEqualTo(transaction);
        tracerImpl.deactivate(transaction);
        assertThat(tracerImpl.currentTransaction()).isEqualTo(transaction);
        tracerImpl.deactivate(transaction);
        assertThat(tracerImpl.getActive()).isNull();
        assertThat(tracerImpl.currentTransaction()).isNull();
        transaction.end();
    }

    @Test
    void testOverrideServiceNameWithoutExplicitServiceName() {
        final ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .buildAndStart();
        tracer.overrideServiceNameForClassLoader(getClass().getClassLoader(), "overridden");

        startTestRootTransaction().end();


        assertThat(reporter.getFirstTransaction().getTraceContext().getServiceName()).isEqualTo("overridden");
    }

    @Test
    void testNotOverrideServiceNameWhenServiceNameConfigured() {
        ConfigurationRegistry localConfig = SpyConfiguration.createSpyConfig(
            Objects.requireNonNull(ConfigSources.fromClasspath("test.elasticapm.with-service-name.properties", ClassLoader.getSystemClassLoader())));

        final ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .reporter(reporter)
            .configurationRegistry(localConfig)
            .buildAndStart();
        tracer.overrideServiceNameForClassLoader(getClass().getClassLoader(), "overridden");

        startTestRootTransaction().end();

        assertThat(reporter.getFirstTransaction().getTraceContext().getServiceName()).isNull();
    }

    @Test
    void testNotOverrideServiceNameWhenDefaultServiceNameConfigured() {
        // In-explicitly affecting the discovered default service name
        String command = System.setProperty("sun.java.command", "TEST_SERVICE_NAME");

        ConfigurationRegistry localConfig = SpyConfiguration.createSpyConfig(
            Objects.requireNonNull(ConfigSources.fromClasspath("test.elasticapm.with-service-name.properties", ClassLoader.getSystemClassLoader()))
        );
        final ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .reporter(reporter)
            .configurationRegistry(localConfig)
            .buildAndStart();
        tracer.overrideServiceNameForClassLoader(getClass().getClassLoader(), "overridden");
        startTestRootTransaction().end();

        CoreConfiguration coreConfig = localConfig.getConfig(CoreConfiguration.class);
        assertThat(ServiceNameUtil.getDefaultServiceName()).isEqualTo(coreConfig.getServiceName());
        assertThat(reporter.getFirstTransaction().getTraceContext().getServiceName()).isNull();
        if (command != null) {
            System.setProperty("sun.java.command", command);
        } else {
            System.clearProperty("sun.java.command");
        }
    }

    @Test
    void testCaptureExceptionAndGetErrorId() {
        Transaction transaction = startTestRootTransaction();
        String errorId = transaction.captureExceptionAndGetErrorId(new Exception("test"));
        transaction.end();
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.FAILURE);

        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(errorId).isNotNull();
        ErrorCapture error = reporter.getFirstError();
        assertThat(error.getTraceContext().getId().toString()).isEqualTo(errorId);
    }

    @Test
    void testCaptureExceptionWithTransactionName() {
        Transaction transaction = startTestRootTransaction().withName("My Transaction");
        try (Scope scope = transaction.activateInScope()) {
            transaction.captureException(new Exception("test"));
            transaction.end();
        }

        assertThat(reporter.getErrors()).hasSize(1);
        ErrorCapture error = reporter.getFirstError();
        assertThat(error.getTransactionInfo().getName().toString()).isEqualTo("My Transaction");
    }

}
