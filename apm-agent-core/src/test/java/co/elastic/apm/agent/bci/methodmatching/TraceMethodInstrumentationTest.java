/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.bci.methodmatching;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class TraceMethodInstrumentationTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;
    private CoreConfiguration coreConfiguration;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        reporter = new MockReporter();
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        coreConfiguration = config.getConfig(CoreConfiguration.class);
        when(coreConfiguration.getTraceMethods()).thenReturn(Arrays.asList(
            MethodMatcher.of("private co.elastic.apm.agent.bci.methodmatching.TraceMethodInstrumentationTest$TestClass#traceMe*()"),
            MethodMatcher.of("private co.elastic.apm.agent.bci.methodmatching.TraceMethodInstrumentationTest$TestDiscardableMethods#*"),
            MethodMatcher.of("private co.elastic.apm.agent.bci.methodmatching.TraceMethodInstrumentationTest$TestErrorCapture#*"),
            MethodMatcher.of("co.elastic.apm.agent.bci.methodmatching.TraceMethodInstrumentationTest$TestExcludeConstructor#*"),
            MethodMatcher.of("public @co.elastic.apm.agent.bci.methodmatching.TraceMethodInstrumentationTest$CustomAnnotation co.elastic.apm.agent.bci.methodmatching*"),
            MethodMatcher.of("public @@co.elastic.apm.agent.bci.methodmatching.TraceMethodInstrumentationTest$Meta* co.elastic.apm.agent.bci.methodmatching*"))
        );
        when(coreConfiguration.getMethodsExcludedFromInstrumentation()).thenReturn(Arrays.asList(
            WildcardMatcher.valueOf("*exclude*"),
            WildcardMatcher.valueOf("manuallyTraced")));

        Set<String> tags = testInfo.getTags();
        if (!tags.isEmpty()) {
            when(coreConfiguration.getTraceMethodsDurationThreshold()).thenReturn(TimeDuration.of(tags.iterator().next()));
        }

        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
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
        when(coreConfiguration.getTraceMethods()).thenReturn(
            List.of(MethodMatcher.of("private co.elastic.apm.agent.bci.methodmatching.TraceMethodInstrumentationTest$TestClass#traceMe()")));
        ElasticApmAgent.reInitInstrumentation().get();
        TestClass.traceMe();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("TestClass#traceMe");
        assertThat(reporter.getSpans()).hasSize(0);
    }

    @Test
    void testTraceMethodNonSampledTransaction() {
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(false), 0, getClass().getClassLoader());
        transaction.withName("not sampled");
        try (Scope scope = transaction.activateInScope()) {
            TestClass.traceMe();
        }
        transaction.end();

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("not sampled");
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
    @Tag("200ms")
    void testDiscardMethods_DiscardAll() {
        new TestDiscardableMethods(tracer).root(false);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(0);
    }

    @Test
    @Tag("200ms")
    void testDiscardMethods_Manual() {
        new TestDiscardableMethods(tracer).root(true);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(3);
    }

    @Test
    @Tag("50ms")
    void testDiscardMethods_ThresholdCrossed() {
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
    @Tag("50ms")
    void testErrorCapture_TraceErrorBranch() {
        new TestErrorCapture().root();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(2);
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
            tracer.getActive().createSpan()
                .activate()
                .deactivate()
                .end();
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
