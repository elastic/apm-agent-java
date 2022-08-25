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
package co.elastic.apm.agent.tracemethods;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.matcher.MethodMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

class TraceMethodInstrumentationTest {

    private MockReporter reporter;
    private TestObjectPoolFactory objectPoolFactory;
    private ElasticApmTracer tracer;
    private CoreConfiguration coreConfiguration;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        reporter = mockInstrumentationSetup.getReporter();
        objectPoolFactory = mockInstrumentationSetup.getObjectPoolFactory();
        ConfigurationRegistry config = mockInstrumentationSetup.getConfig();
        coreConfiguration = config.getConfig(CoreConfiguration.class);
        doReturn(Arrays.asList(
            MethodMatcher.of("private co.elastic.apm.agent.tracemethods.TraceMethodInstrumentationTest$TestClass#traceMe*()"),
            MethodMatcher.of("private co.elastic.apm.agent.tracemethods.TraceMethodInstrumentationTest$TestDiscardableMethods#*"),
            MethodMatcher.of("private co.elastic.apm.agent.tracemethods.TraceMethodInstrumentationTest$TestErrorCapture#*"),
            MethodMatcher.of("co.elastic.apm.agent.tracemethods.TraceMethodInstrumentationTest$TestExcludeConstructor#*"),
            MethodMatcher.of("public @co.elastic.apm.agent.tracemethods.TraceMethodInstrumentationTest$CustomAnnotation co.elastic.apm.agent.tracemethods*"),
            MethodMatcher.of("public @@co.elastic.apm.agent.tracemethods.TraceMethodInstrumentationTest$Meta* co.elastic.apm.agent.tracemethods*"))
        ).when(coreConfiguration).getTraceMethods();
        doReturn(Arrays.asList(
                WildcardMatcher.valueOf("*exclude*"),
                WildcardMatcher.valueOf("manuallyTraced"))
        ).when(coreConfiguration).getMethodsExcludedFromInstrumentation();

        for (String tag : testInfo.getTags()) {
            TimeDuration duration = TimeDuration.of(tag.split("=")[1]);
            if (tag.startsWith("span_min_duration=")) {
                doReturn(duration).when(coreConfiguration).getSpanMinDuration();
            }
            if (tag.startsWith("trace_methods_duration_threshold=")) {
                doReturn(duration).when(coreConfiguration).getTraceMethodsDurationThreshold();
            }
        }

        tracer = mockInstrumentationSetup.getTracer();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @AfterEach
    void tearDown() {
        ElasticApmAgent.reset();

    }

    @Test
    void testTraceMethod() {
        TestClass.traceMe();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("TestClass#traceMe");
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("TestClass#traceMeToo");
    }

    @Test
    void testReInitTraceMethod() throws Exception {
        doReturn(List.of(MethodMatcher.of("private co.elastic.apm.agent.tracemethods.TraceMethodInstrumentationTest$TestClass#traceMe()")))
            .when(coreConfiguration).getTraceMethods();
        ElasticApmAgent.reInitInstrumentation().get();
        TestClass.traceMe();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("TestClass#traceMe");
        // if original configuration was used, a span would have been created - see `testTraceMethod`
        assertThat(reporter.getSpans()).hasSize(0);
    }

    @Test
    void testExcludedMethod() {
        TestClass.traceMeAsWell();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("TestClass#traceMeAsWell");
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("TestClass#traceMeToo");
    }

    @Test
    void testNotMatched_VisibilityModifier() {
        TestClass.traceMeNot();
        assertThat(reporter.getTransactions()).isEmpty();
    }

    @Test
    void testNotMatched_Parameters() {
        TestClass.traceMeNot(false);
        assertThat(reporter.getTransactions()).isEmpty();
    }

    // Byte Buddy can't catch exceptions (onThrowable = Throwable.class) during a constructor call:
    @Test
    void testNotMatched_Constructor() {
        final TestExcludeConstructor testClass = new TestExcludeConstructor();
        // the static initializer should not be traced
        assertThat(reporter.getTransactions()).isEmpty();
        testClass.traceMe();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("TestExcludeConstructor#traceMe");
    }

    @Test
    void testDiscardMethods_TraceAll() {
        new TestDiscardableMethods(tracer).root(true);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(7);
    }

    @Test
    void testAgentPaused() {
        TracerInternalApiUtils.pauseTracer(tracer);
        int transactionCount = objectPoolFactory.getTransactionPool().getRequestedObjectCount();
        int spanCount = objectPoolFactory.getSpanPool().getRequestedObjectCount();
        new TestDiscardableMethods(tracer).root(true);
        assertThat(reporter.getTransactions()).hasSize(0);
        assertThat(reporter.getSpans()).hasSize(0);
        assertThat(objectPoolFactory.getTransactionPool().getRequestedObjectCount()).isEqualTo(transactionCount);
        assertThat(objectPoolFactory.getSpanPool().getRequestedObjectCount()).isEqualTo(spanCount);
    }

    @Test
    @Tag("span_min_duration=200ms")
    void testDiscardMethods_DiscardAll() {
        new TestDiscardableMethods(tracer).root(false);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(0);
    }

    @Test
    @Tag("span_min_duration=50ms")
    @Tag("trace_methods_duration_threshold=200ms")
    void testDiscardMethods_DiscardAll_HigherWinns_SpecificThreshold() {
        new TestDiscardableMethods(tracer).root(false);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(0);
    }

    @Test
    @Tag("span_min_duration=200ms")
    @Tag("trace_methods_duration_threshold=50ms")
    void testDiscardMethods_DiscardAll_HigherWinns_GenericThreshold() {
        new TestDiscardableMethods(tracer).root(false);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(0);
    }

    @Test
    @Tag("span_min_duration=200ms")
    void testDiscardMethods_Manual() {
        new TestDiscardableMethods(tracer).root(true);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(3);
    }

    @Test
    @Tag("span_min_duration=50ms")
    void testDiscardMethods_GeneralThresholdCrossed() {
        new TestDiscardableMethods(tracer).root(true);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(5);
    }

    @Test
    @Tag("trace_methods_duration_threshold=50ms")
    void testDiscardMethods_SpecificThresholdCrossed() {
        new TestDiscardableMethods(tracer).root(true);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(5);
    }

    @Test
    void testErrorCapture_TraceAll() {
        new TestErrorCapture().root();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(3);
        assertThat(reporter.getErrors()).hasSize(1);
    }

    @Test
    @Tag("span_min_duration=50ms")
    void testErrorCapture_TraceErrorBranch() {
        new TestErrorCapture().root();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans().stream().map(Span::getNameAsString)).containsExactly("TestErrorCapture#throwException", "TestErrorCapture#catchException");
        assertThat(reporter.getErrors()).hasSize(1);
    }

    @Test
    void testAnnotatedClass() {
        AnnotatedClass annotatedClass = new AnnotatedClass();
        assertThat(reporter.getTransactions()).isEmpty();
        annotatedClass.traceMe();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("AnnotatedClass#traceMe");
    }

    @Test
    void testNotTracedAnnotatedClass() {
        NotTracedAnnotatedClass notTracedAnnotatedClass = new NotTracedAnnotatedClass();
        notTracedAnnotatedClass.traceMeNot();
        assertThat(reporter.getTransactions()).isEmpty();
    }

    @Test
    void testMetaAnnotatedClass() {
        MetaAnnotatedClass metaAnnotatedClass = new MetaAnnotatedClass();
        assertThat(reporter.getTransactions()).isEmpty();
        metaAnnotatedClass.traceMe();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("MetaAnnotatedClass#traceMe");
    }

    public static class TestClass {
        // not traced because visibility modifier does not match
        public static void traceMeNot() {
        }

        private static void traceMeNot(boolean doesNotMatchParameterMatcher) {
        }

        private static void traceMe() {
            traceMeToo();
        }

        private static void traceMeAsWell() {
            traceMeToo();
            traceMeNotExcludeMe();
        }

        private static void traceMeToo() {

        }

        private static void traceMeNotExcludeMe() {

        }
    }

    public static class TestExcludeConstructor {
        static {
        }

        public TestExcludeConstructor() {
        }

        public void traceMe() {
        }
    }

    public static class TestDiscardableMethods {
        private final ElasticApmTracer tracer;

        TestDiscardableMethods(ElasticApmTracer tracer) {
            this.tracer = tracer;
        }

        /**
         * Calling root(true) results in the following method call tree:
         * <p>
         * root
         *  |
         *  --- decide
         *  |     |
         *  |     --- mainMethod
         *  |              |
         *  |              --- manuallyTraced
         *  |              |
         *  |              --- beforeLongMethod
         *  |                          |
         *  |                          --- longMethod
         *  |
         *  --- decide
         *        |
         *        --- sideMethod
         * <p>
         * <p>
         * Calling root(false) will result in the same method call tree, except from the manuallyTraced() method
         */
        private void root(boolean invokeManual) {
            decide(false, invokeManual);
            decide(true, invokeManual);
        }

        private void decide(boolean sideBranch, boolean invokeManual) {
            if (sideBranch) {
                sideMethod();
            } else {
                mainMethod(invokeManual);
            }
        }

        private void sideMethod() {
            // do nothing
        }

        private void mainMethod(boolean invokeManual) {
            if (invokeManual) {
                manuallyTraced();
            }
            beforeLongMethod();
        }

        private void manuallyTraced() {
            AbstractSpan<?> active = tracer.getActive();
            if (active != null) {
                Span span = active.createSpan();
                span.propagateTraceContext(new HashMap<>(), (k, v, m) -> m.put(k, v));
                span.end();
            }
        }

        private void beforeLongMethod() {
            longMethod();
        }

        private void longMethod() {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static class TestErrorCapture {
        private void root() {
            catchException();
            someMethod();
        }

        private void catchException() {
            try {
                throwException();
            } catch (Exception e) {
            }
        }

        private void throwException() {
            throw new RuntimeException("Test Exception");
        }

        private void someMethod() {
        }
    }

    @CustomAnnotation
    public static class AnnotatedClass {

        public void traceMe() {
        }

    }

    @NotTracedAnnotation
    public static class NotTracedAnnotatedClass {

        public void traceMeNot() {
        }

    }


    @MetaAnnotated
    public static class MetaAnnotatedClass {

        public void traceMe() {
        }

    }


    @Retention(RUNTIME)
    public @interface CustomAnnotation {
    }

    @Retention(RUNTIME)
    public @interface NotTracedAnnotation {
    }

    @Retention(RUNTIME)
    public @interface MetaAnnotation {
    }

    @Retention(RUNTIME)
    @MetaAnnotation
    public @interface MetaAnnotated {
    }
}
