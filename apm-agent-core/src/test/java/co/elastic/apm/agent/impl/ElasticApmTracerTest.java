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
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.AutoDetectedServiceInfo;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.impl.transaction.*;
import co.elastic.apm.agent.impl.baggage.BaggageContext;
import co.elastic.apm.agent.tracer.service.ServiceInfo;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.source.ConfigSources;
import co.elastic.apm.agent.impl.baggage.BaggageImpl;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfigurationImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.tracer.metrics.Labels;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ElasticApmTracerTest {

    private ElasticApmTracer tracerImpl;
    private MockReporter reporter;
    private ConfigurationRegistry config;

    private ApmServerClient apmServerClient;

    private TestObjectPoolFactory objectPoolFactory;

    @BeforeEach
    void setUp() {
        doSetup(conf -> {
        });
    }

    void doSetup(Consumer<ConfigurationRegistry> configCustomizer) {
        objectPoolFactory = new TestObjectPoolFactory();
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        configCustomizer.accept(config);

        apmServerClient = new ApmServerClient(config);
        apmServerClient = mock(ApmServerClient.class, delegatesTo(apmServerClient));

        tracerImpl = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .withObjectPoolFactory(objectPoolFactory)
            .withApmServerClient(apmServerClient)
            .buildAndStart();
    }

    void setupWithCustomConfig(Consumer<ConfigurationRegistry> configCustomizer) {
        cleanupAndCheck(); //cleanup @BeforeEach
        doSetup(configCustomizer);
    }

    @AfterEach
    void cleanupAndCheck() {
        reporter.assertRecycledAfterDecrementingReferences();
        objectPoolFactory.checkAllPooledObjectsHaveBeenRecycled();
        tracerImpl.resetServiceInfoOverrides();
    }

    @Test
    void testThreadLocalStorage() {
        TransactionImpl transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            assertThat(tracerImpl.currentTransaction()).isSameAs(transaction);
            SpanImpl span = tracerImpl.getActive().createSpan();
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
        TransactionImpl transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            assertThat(tracerImpl.currentTransaction()).isSameAs(transaction);
            SpanImpl span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                assertThat(tracerImpl.getActive()).isSameAs(span);
                assertThat(span.isChildOf(transaction)).isTrue();
                SpanImpl nestedSpan = tracerImpl.getActive().createSpan();
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
        doReturn(-1L).when(tracerImpl.getConfig(StacktraceConfigurationImpl.class)).getSpanStackTraceMinDurationMs();

        TransactionImpl transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            SpanImpl span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                span.end();
            }
            transaction.end();
        }
        assertThat(reporter.getFirstSpan().getStacktrace()).isNull();
    }

    @Test
    void testEnableStacktraces() throws InterruptedException {
        doReturn(0L).when(tracerImpl.getConfig(StacktraceConfigurationImpl.class)).getSpanStackTraceMinDurationMs();
        TransactionImpl transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            SpanImpl span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                Thread.sleep(10);
                stackTraceEndSpan(span);
            }
            transaction.end();
        }
        Throwable stackTrace = reporter.getFirstSpan().getStacktrace();
        assertThat(stackTrace).isNotNull();
        assertThat(Arrays.stream(stackTrace.getStackTrace()).filter(stackTraceElement ->
            stackTraceElement.getMethodName().equals("stackTraceEndSpan")
                && stackTraceElement.getClassName().equals(ElasticApmTracerTest.class.getName()))).hasSize(1);
    }

    private static void stackTraceEndSpan(SpanImpl span) {
        // dummy method used just to verify that the captured stack trace contains it
        span.end();
    }

    @Test
    void testDisableStacktracesForFastSpans() {
        doReturn(100L).when(tracerImpl.getConfig(StacktraceConfigurationImpl.class)).getSpanStackTraceMinDurationMs();
        TransactionImpl transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            SpanImpl span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                span.end();
            }
            transaction.end();
        }
        assertThat(reporter.getFirstSpan().getStacktrace()).isNull();

    }

    @Test
    void testEnableStacktracesForSlowSpans() throws InterruptedException {
        doReturn(1L).when(tracerImpl.getConfig(StacktraceConfigurationImpl.class)).getSpanStackTraceMinDurationMs();
        TransactionImpl transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            SpanImpl span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                Thread.sleep(10);
                span.end();
            }
            transaction.end();
        }
        assertThat(reporter.getFirstSpan().getStacktrace()).isNotNull();
    }

    @Nullable
    private TransactionImpl startTestRootTransaction() {
        return tracerImpl.startRootTransaction(getClass().getClassLoader());
    }

    @Test
    void testRecordException() {
        tracerImpl.captureAndReportException(new Exception("test"), getClass().getClassLoader());
        assertThat(reporter.getErrors()).hasSize(1);
        ErrorCaptureImpl error = reporter.getFirstError();
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

        doReturn(wildcardList).when(config.getConfig(CoreConfigurationImpl.class)).getIgnoreExceptions();

        tracerImpl.captureAndReportException(new DummyException1(), getClass().getClassLoader());
        tracerImpl.captureAndReportException(new DummyException2(), getClass().getClassLoader());
        assertThat(reporter.getErrors()).isEmpty();
    }

    @Test
    void testTransactionNameGrouping() {
        doReturn(List.of(WildcardMatcher.valueOf("GET /foo/*/bar"))).when(config.getConfig(CoreConfigurationImpl.class)).getTransactionNameGroups();

        TransactionImpl transaction = tracerImpl.startRootTransaction(null).appendToName("GET ").appendToName("/foo/42/bar");
        try (Scope scope = transaction.activateInScope()) {
            transaction.captureException(new RuntimeException("Test error capturing"));
        }
        transaction.end();
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("GET /foo/*/bar");
        assertThat(reporter.getFirstError().getTransactionInfo().getName().toString()).isEqualTo("GET /foo/*/bar");
        tracerImpl.getMetricRegistry().flipPhaseAndReport(metricSets -> {
            assertThat(metricSets.get(Labels.Mutable.of()
                .transactionName("GET /foo/*/bar")
                .transactionType("custom")
                .spanType("app")))
                .isNotNull();
        });

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
        TransactionImpl transaction = tracerImpl.startRootTransaction(ConstantSampler.of(sampled), -1, null);
        transaction.withType("request");
        try (Scope scope = transaction.activateInScope()) {
            transaction.getContext().getRequest()
                .addHeader("foo", "bar")
                .withMethod("GET")
                .getUrl()
                .withPathname("/foo");
            tracerImpl.currentTransaction().captureException(new Exception("from transaction"));

            SpanImpl span = transaction.createSpan().activate();
            span.captureException(new Exception("from span"));

            List<ErrorCaptureImpl> errors = reporter.getErrors();
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

    private ErrorCaptureImpl validateSingleError(AbstractSpanImpl<?> span, boolean sampled, TransactionImpl correspondingTransaction) {
        assertThat(reporter.getErrors()).hasSize(1);
        return validateError(reporter.getFirstError(), span, sampled, correspondingTransaction);
    }

    private ErrorCaptureImpl validateError(ErrorCaptureImpl error, AbstractSpanImpl<?> span, boolean sampled, TransactionImpl transaction) {
        assertThat(error.getTraceContext().isChildOf(span.getTraceContext()))
            .describedAs("error trace context [%s] should be a child of span trace context [%s]", error.getTraceContext(), span.getTraceContext())
            .isTrue();
        assertThat(error.getTransactionInfo().isSampled()).isEqualTo(sampled);
        if (!transaction.getNameAsString().isEmpty()) {
            assertThat(error.getTransactionInfo().getName().toString()).isEqualTo(transaction.getNameAsString());
        }
        assertThat(error.getTransactionInfo().getType()).isEqualTo(transaction.getType());
        return error;
    }

    @Test
    void testEnableDropSpans() {
        setupWithCustomConfig(conf -> {
            doReturn(1).when(config.getConfig(CoreConfigurationImpl.class)).getTransactionMaxSpans();
        });
        TransactionImpl transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            SpanImpl span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                assertThat(tracerImpl.getActive()).isSameAs(span); //ensure ActiveStack limit is not reached
                assertThat(span.isSampled()).isTrue();
                span.end();
            }
            SpanImpl span2 = tracerImpl.getActive().createSpan();
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
    void testActivationStackOverflow() {
        doReturn(2).when(config.getConfig(CoreConfigurationImpl.class)).getTransactionMaxSpans();

        ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .withApmServerClient(mock(ApmServerClient.class))
            .reporter(reporter)
            .withObjectPoolFactory(objectPoolFactory)
            .buildAndStart();

        doWithNestedBaggageActivations(() -> {
            TransactionImpl transaction = tracer.startRootTransaction(getClass().getClassLoader());
            assertThat(tracer.getActive()).isNull();
            try (Scope scope = transaction.activateInScope()) {
                assertThat(tracer.getActive()).isEqualTo(transaction);
                SpanImpl child1 = transaction.createSpan();
                try (Scope childScope = child1.activateInScope()) {
                    assertThat(tracer.getActive()).isEqualTo(child1);
                    SpanImpl grandchild1 = child1.createSpan();
                    try (Scope grandchildScope = grandchild1.activateInScope()) {
                        // latter activation should not be applied due to activation stack overflow
                        assertThat(tracer.getActive()).isEqualTo(child1);
                        SpanImpl ggc = grandchild1.createSpan();
                        try (Scope ggcScope = ggc.activateInScope()) {
                            assertThat(tracer.getActive()).isEqualTo(child1);
                            ggc.end();
                        }
                        grandchild1.end();
                    }
                    assertThat(tracer.getActive()).isEqualTo(child1);
                    child1.end();
                }
                assertThat(tracer.getActive()).isEqualTo(transaction);
                SpanImpl child2 = transaction.createSpan();
                try (Scope childScope = child2.activateInScope()) {
                    assertThat(tracer.getActive()).isEqualTo(child2);
                    SpanImpl grandchild2 = child2.createSpan();
                    try (Scope grandchildScope = grandchild2.activateInScope()) {
                        // latter activation should not be applied due to activation stack overflow
                        assertThat(tracer.getActive()).isEqualTo(child2);
                        grandchild2.end();
                    }
                    assertThat(tracer.getActive()).isEqualTo(child2);
                    child2.end();
                }
                assertThat(tracer.getActive()).isEqualTo(transaction);
                transaction.end();
            }
        }, tracer, ElasticApmTracer.ACTIVATION_STACK_BASE_SIZE);
        assertThat(tracer.getActive()).isNull();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(2);
    }

    private void doWithNestedBaggageActivations(Runnable r, ElasticApmTracer tracer, int nestedCount) {
        if (nestedCount == 0) {
            r.run();
            return;
        }
        BaggageContext baggageContext = tracer.currentContext().withUpdatedBaggage().buildContext();
        try (Scope scope = baggageContext.activateInScope()) {
            doWithNestedBaggageActivations(r, tracer, nestedCount - 1);
        }
    }

    @Test
    void testPause() {
        tracerImpl.pause();
        assertThat(startTestRootTransaction()).isNull();
    }

    @Test
    void testPauseMidTransaction() {
        TransactionImpl transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            assertThat(tracerImpl.currentTransaction()).isSameAs(transaction);
            tracerImpl.pause();
            SpanImpl span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                span.withName("test");
                assertThat(span.getNameAsString()).isEqualTo("test");
                assertThat(tracerImpl.getActive()).isSameAs(span);
                assertThat(span.isChildOf(transaction)).isTrue();
                span.end();
            }
            SpanImpl span2 = tracerImpl.getActive().createSpan();
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
    void testSamplingNone_sendUnsampled() throws IOException {
        doReturn(true).when(apmServerClient).supportsKeepingUnsampledTransaction();
        testSamplingNone(true);
    }

    @Test
    void testSamplingNone_dropUnsampled() throws IOException {
        doReturn(false).when(apmServerClient).supportsKeepingUnsampledTransaction();
        testSamplingNone(false);
    }

    void testSamplingNone(boolean keepUnsampled) throws IOException {

        config.getConfig(CoreConfigurationImpl.class).getSampleRate().update(0.0, SpyConfiguration.CONFIG_SOURCE_NAME);
        TransactionImpl transaction = startTestRootTransaction().withType("request");

        try (Scope scope = transaction.activateInScope()) {
            transaction.setUser("1", "jon.doe@example.com", "jondoe", "domain");
            SpanImpl span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                span.end();
            }
            transaction.end();
        }

        if (keepUnsampled) {
            // we do report non-sampled transactions (without the context)
            assertThat(reporter.getTransactions())
                .describedAs("non-sampled transaction should be kept")
                .hasSize(1);

            assertThat(reporter.getFirstTransaction().getType()).isEqualTo("request");
            assertThat(reporter.getFirstTransaction().isSampled()).isFalse();

            assertThat(reporter.getFirstTransaction().getContext().getUser().getEmail())
                .describedAs("non-sampled transaction context should be drpped")
                .isNull();
        } else {
            assertThat(reporter.getTransactions())
                .describedAs("non-sampled transaction should be dropped")
                .hasSize(0);

        }

        assertThat(reporter.getSpans())
            .describedAs("spans within non-sampled transactions should dropped")
            .hasSize(0);

    }

    @Test
    void testTransactionWithParentReference() {
        final Map<String, String> headerMap = Map.of(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final TransactionImpl transaction = tracerImpl.startChildTransaction(headerMap, TextHeaderMapAccessor.INSTANCE, ConstantSampler.of(false), 0, null);
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
        final TransactionImpl transaction = tracerImpl.startChildTransaction(new HashMap<>(), TextHeaderMapAccessor.INSTANCE, ConstantSampler.of(true), 0, null);
        final SpanImpl span = transaction.createSpan(10);
        span.end(20);

        transaction.end(30);

        assertThat(transaction.getTimestamp()).isEqualTo(0);
        assertThat(transaction.getDuration()).isEqualTo(30);
        assertThat(span.getTimestamp()).isEqualTo(10);
        assertThat(span.getDuration()).isEqualTo(10);
    }

    @Test
    void testTimestampSanitization() {
        final TransactionImpl transaction = tracerImpl.startChildTransaction(new HashMap<>(), TextHeaderMapAccessor.INSTANCE, ConstantSampler.of(true), 10, null);
        final SpanImpl span = transaction.createSpan(20);
        span.end(10);

        transaction.end(5);

        assertThat(transaction.getTimestamp()).isEqualTo(10);
        assertThat(transaction.getDuration()).isEqualTo(0);
        assertThat(span.getTimestamp()).isEqualTo(20);
        assertThat(span.getDuration()).isEqualTo(0);
    }

    @Test
    void testStartSpanAfterTransactionHasEnded() {
        final TransactionImpl transaction = startTestRootTransaction();
        assertThat(transaction).isNotNull();
        transaction.incrementReferences();
        transaction.end();


        try (Scope transactionScope = transaction.activateInScope()) {
            assertThat(tracerImpl.getActive()).isEqualTo(transaction);
            final SpanImpl span = tracerImpl.startSpan(transaction, BaggageImpl.EMPTY, -1);
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
        final TransactionImpl transaction = startTestRootTransaction();
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
    void testEmptyContextActivation() {
        final TransactionImpl transaction = startTestRootTransaction();
        assertThat(tracerImpl.currentContext().getTransaction()).isNull();
        tracerImpl.activate(transaction);
        assertThat(tracerImpl.currentContext().getTransaction()).isEqualTo(transaction);

        EmptyTraceState empty = new EmptyTraceState(tracerImpl);
        empty.activate();
        assertThat(tracerImpl.currentContext().getTransaction()).isNull();

        empty.deactivate();
        assertThat(tracerImpl.currentContext().getTransaction()).isEqualTo(transaction);
        tracerImpl.deactivate(transaction);
        assertThat(tracerImpl.currentContext().getTransaction()).isNull();
        transaction.end();
    }

    @Test
    void testOverrideServiceNameWithoutExplicitServiceName() {
        final ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .buildAndStart();

        ClassLoader cl = getClass().getClassLoader();
        ServiceInfo overridden = ServiceInfo.of("overridden");
        tracer.setServiceInfoForClassLoader(cl, overridden);
        assertThat(tracer.getServiceInfoForClassLoader(cl)).isEqualTo(overridden);

        startTestRootTransaction().end();

        checkServiceInfo(reporter.getFirstTransaction(), overridden);
    }

    @Test
    void testNotOverrideServiceNameWhenServiceNameConfigured() {
        ConfigurationRegistry localConfig = SpyConfiguration.createSpyConfig(
            Objects.requireNonNull(ConfigSources.fromClasspath("test.elasticapm.with-service-name.properties", ClassLoader.getSystemClassLoader())));

        final ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .reporter(reporter)
            .configurationRegistry(localConfig)
            .buildAndStart();
        ClassLoader cl = getClass().getClassLoader();
        tracer.setServiceInfoForClassLoader(cl, ServiceInfo.of("overridden"));
        assertThat(tracer.getServiceInfoForClassLoader(cl)).isNull();

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

        ClassLoader cl = getClass().getClassLoader();
        tracer.setServiceInfoForClassLoader(cl, ServiceInfo.of("overridden"));
        assertThat(tracer.getServiceInfoForClassLoader(cl)).isNull();

        startTestRootTransaction().end();

        CoreConfigurationImpl coreConfig = localConfig.getConfig(CoreConfigurationImpl.class);

        assertThat(AutoDetectedServiceInfo.autoDetect(System.getProperties(), System.getenv()))
            .isEqualTo(ServiceInfo.of(coreConfig.getServiceName()));

        assertThat(reporter.getFirstTransaction().getTraceContext().getServiceName()).isNull();
        if (command != null) {
            System.setProperty("sun.java.command", command);
        } else {
            System.clearProperty("sun.java.command");
        }
    }

    private static void checkServiceInfo(TransactionImpl transaction, ServiceInfo expected) {
        TraceContextImpl traceContext = transaction.getTraceContext();
        assertThat(traceContext.getServiceName()).isEqualTo(expected.getServiceName());
        assertThat(traceContext.getServiceVersion()).isEqualTo(expected.getServiceVersion());
    }

    @Test
    void testOverrideServiceVersionWithoutExplicitServiceVersion() {
        final ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .reporter(reporter)
            .buildAndStart();

        ServiceInfo overridden = ServiceInfo.of("overridden_name", "overridden_version");
        tracer.setServiceInfoForClassLoader(getClass().getClassLoader(), overridden);

        startTestRootTransaction().end();

        checkServiceInfo(reporter.getFirstTransaction(), overridden);
    }

    @Test
    void testNotOverrideServiceVersionWhenServiceVersionConfigured() {
        ConfigurationRegistry localConfig = SpyConfiguration.createSpyConfig(ConfigSources.fromClasspath("test.elasticapm.with-service-version.properties", ClassLoader.getSystemClassLoader()));
        final ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .reporter(reporter)
            .configurationRegistry(localConfig)
            .buildAndStart();

        ServiceInfo overridden = ServiceInfo.of("overridden_name", "overridden_version");
        ClassLoader cl = getClass().getClassLoader();
        tracer.setServiceInfoForClassLoader(cl, overridden);
        assertThat(tracer.getServiceInfoForClassLoader(cl)).isEqualTo(overridden);

        startTestRootTransaction().end();

        checkServiceInfo(reporter.getFirstTransaction(), overridden);
    }

    @Test
    void testCaptureExceptionAndGetErrorId() {
        TransactionImpl transaction = startTestRootTransaction();
        String errorId = transaction.captureExceptionAndGetErrorId(new Exception("test"));
        transaction.end();
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.FAILURE);

        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(errorId).isNotNull();
        ErrorCaptureImpl error = reporter.getFirstError();
        assertThat(error.getTraceContext().getId().toString()).isEqualTo(errorId);
    }

    @Test
    void testCaptureExceptionWithTransactionName() {
        TransactionImpl transaction = startTestRootTransaction().withName("My Transaction");
        try (Scope scope = transaction.activateInScope()) {
            transaction.captureException(new Exception("test"));
            transaction.end();
        }

        assertThat(reporter.getErrors()).hasSize(1);
        ErrorCaptureImpl error = reporter.getFirstError();
        assertThat(error.getTransactionInfo().getName().toString()).isEqualTo("My Transaction");
    }

    @Test
    void testContextWrapping() {
        TransactionImpl transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {

            assertThat(tracerImpl.currentContext())
                .describedAs("native span/transaction is not wrapped")
                .isSameAs(transaction);

            TestContext testContext = tracerImpl.wrapActiveContextIfRequired(TestContext.class, () -> new TestContext());

            assertThat(tracerImpl.wrapActiveContextIfRequired(TestContext.class, () -> new TestContext()))
                .describedAs("wrap should only happen once and if required")
                .isSameAs(testContext);

            assertThat(tracerImpl.currentContext())
                .describedAs("after wrapping the active context remains the same")
                .isSameAs(transaction);

            transaction.end();
        }

    }

    @Test
    void testUnknownConfiguration() {
        assertThatThrownBy(() -> tracerImpl.getConfig(Object.class))
            .isInstanceOf(IllegalStateException.class);

        ConfigurationOptionProvider unregisteredConfigProvider = new ConfigurationOptionProvider() {
        };

        assertThatThrownBy(() -> tracerImpl.getConfig(unregisteredConfigProvider.getClass()))
            .isInstanceOf(IllegalStateException.class);

    }

    private static final class TestContext extends TraceStateImpl<TestContext> {

        private TestContext() {
            super(null);
        }

        @Nullable
        @Override
        public AbstractSpanImpl<?> getSpan() {
            return null;
        }

        @Override
        public BaggageImpl getBaggage() {
            return null;
        }

        @Override
        public void incrementReferences() {

        }

        @Override
        public void decrementReferences() {

        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCaptureThreadDetails(boolean enabled) {

        doReturn(enabled).when(tracerImpl.getConfig(CoreConfigurationImpl.class)).isCaptureThreadOnStart();

        TransactionImpl transaction = startTestRootTransaction();
        try (Scope scope = transaction.activateInScope()) {
            SpanImpl span = tracerImpl.getActive().createSpan();
            try (Scope spanScope = span.activateInScope()) {
                span.end();
            }
            transaction.end();
        }
        Stream.of(reporter.getFirstTransaction().getContext(), reporter.getFirstSpan().getContext())
            .forEach(c -> {
                if (enabled) {
                    assertThat(c.getLabel("thread_id")).isNotNull();
                    assertThat(c.getLabel("thread_name")).isNotNull();
                } else {
                    assertThat(c.getLabel("thread_id")).isNull();
                    assertThat(c.getLabel("thread_name")).isNull();
                }

            });

    }
}
