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
package co.elastic.apm.api;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * Test annotation inheritance. Does not inherit from AbstractInstrumentationTest due to non-runtime configuration that
 * must be applied BEFORE agent instrumentation.
 */
class AnnotationInheritanceTest {

    protected static ElasticApmTracer tracer;
    protected static MockReporter reporter;

    @AfterEach
    void cleanup() {
        reporter.resetWithoutRecycling();
    }

    private void init(boolean annotationInheritanceEnabled) {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup(SpyConfiguration.createSpyConfig(), false);
        tracer = mockInstrumentationSetup.getTracer();
        doReturn(annotationInheritanceEnabled).when(tracer.getConfig(CoreConfigurationImpl.class)).isEnablePublicApiAnnotationInheritance();
        reporter = mockInstrumentationSetup.getReporter();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    private void reset() {
        ElasticApmAgent.reset();
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class DisabledPublicApiAnnotationInheritance {

        @BeforeAll
        void beforeAll() {
            init(false);
        }

        @AfterAll
        void afterAll() {
            reset();
        }

        @Test
        void testClassWithAnnotations() {
            invokeApiMethods(new ClassWithAnnotations());
            assertThat(reporter.getTransactions()).hasSize(3);
            assertThat(reporter.getSpans()).hasSize(1);
        }

        @Test
        void testClassWithoutAnnotations() {
            invokeApiMethods(new ClassWithoutAnnotations());
            assertThat(reporter.getTransactions()).hasSize(1);
            assertThat(reporter.getSpans()).hasSize(0);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class EnabledPublicApiAnnotationInheritance {


        @BeforeAll
        void beforeAll() {
            init(true);
        }

        @AfterAll
        void afterAll() {
            reset();
        }

        private TestClassBase createTestClassInstance(Class<? extends TestClassBase> testClass)
            throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
            Constructor<? extends TestClassBase> declaredConstructor = testClass.getDeclaredConstructor();
            declaredConstructor.setAccessible(true);
            return declaredConstructor.newInstance();
        }

        @Test
        void testClassWithAnnotations() {
            invokeApiMethods(new ClassWithAnnotations());
            assertThat(reporter.getTransactions()).hasSize(3);
            assertThat(reporter.getSpans()).hasSize(1);
        }

        @ParameterizedTest
        @ValueSource(classes = {ClassWithoutAnnotations.class, TransitiveClassWithoutAnnotations.class, InterfaceImplementor.class})
        void testInheritedCaptureTransaction(Class<? extends TestClassBase> testClass) throws Exception {
            TestClassBase instance = createTestClassInstance(testClass);
            instance.captureTransaction();
            checkTransaction(testClass.getSimpleName() + "#captureTransaction");
        }


        @ParameterizedTest
        @ValueSource(classes = {ClassWithoutAnnotations.class, TransitiveClassWithoutAnnotations.class, InterfaceImplementor.class})
        void testInheritedCaptureSpan(Class<? extends TestClassBase> testClass) throws Exception {
            TestClassBase instance = createTestClassInstance(testClass);
            Transaction transaction = ElasticApm.startTransaction();
            try (Scope scope = transaction.activate()) {
                instance.captureSpan();
            }
            transaction.end();
            checkSpan(testClass.getSimpleName() + "#captureSpan");
        }

        @ParameterizedTest
        @ValueSource(classes = {ClassWithoutAnnotations.class, TransitiveClassWithoutAnnotations.class, InterfaceImplementor.class})
        void testInheritedTracedWithoutActiveTransaction(Class<? extends TestClassBase> testClass) throws Exception {
            createTestClassInstance(testClass).traced();
            checkTransaction(testClass.getSimpleName() + "#traced");
        }

        @ParameterizedTest
        @ValueSource(classes = {ClassWithoutAnnotations.class, TransitiveClassWithoutAnnotations.class, InterfaceImplementor.class})
        void testInheritedTracedWithActiveTransaction(Class<? extends TestClassBase> testClass) throws Exception {
            Transaction transaction = ElasticApm.startTransaction();
            try (Scope scope = transaction.activate()) {
                createTestClassInstance(testClass).traced();
            }
            transaction.end();
            checkSpan(testClass.getSimpleName() + "#traced");
        }

        private void checkTransaction(String name) {
            assertThat(reporter.getTransactions()).hasSize(1);
            assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo(name);
            assertThat(reporter.getFirstTransaction().getType()).isEqualTo(Transaction.TYPE_REQUEST);
        }

        private void checkSpan(String name) {
            assertThat(reporter.getSpans()).hasSize(1);
            assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo(name);
            assertThat(reporter.getFirstSpan().getType()).isEqualTo("app");
        }
    }

    private void invokeApiMethods(ClassWithAnnotations classWithAnnotations) {
        classWithAnnotations.captureTransaction();
        Transaction transaction = ElasticApm.startTransaction();
        try (Scope scope = transaction.activate()) {
            classWithAnnotations.captureSpan();
        }
        transaction.end();
        classWithAnnotations.traced();
    }


    abstract static class TestClassBase {
        abstract void captureTransaction();

        abstract void captureSpan();

        abstract void traced();
    }

    static class ClassWithAnnotations extends TestClassBase {
        @CaptureTransaction
        void captureTransaction() {
        }

        @CaptureSpan
        void captureSpan() {
        }

        @Traced
        void traced() {
        }
    }

    static class ClassWithoutAnnotations extends ClassWithAnnotations {
        @Override
        void captureTransaction() {
        }

        @Override
        void captureSpan() {
        }

        @Override
        void traced() {
        }
    }

    static class EmptyClass extends ClassWithAnnotations {
    }

    static class TransitiveClassWithoutAnnotations extends EmptyClass {
        @Override
        void captureTransaction() {
        }

        @Override
        void captureSpan() {
        }

        @Override
        void traced() {
        }
    }

    interface InterfaceWithAnnotations {
        @CaptureTransaction
        void captureTransaction();

        @CaptureSpan
        void captureSpan();

        @Traced
        void traced();
    }

    static class InterfaceImplementor extends TestClassBase implements InterfaceWithAnnotations {

        public void captureTransaction() {
        }

        public void captureSpan() {
        }

        @Override
        public void traced() {
        }
    }

}
