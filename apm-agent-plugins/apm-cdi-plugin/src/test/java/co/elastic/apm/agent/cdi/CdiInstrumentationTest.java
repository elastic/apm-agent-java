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
package co.elastic.apm.agent.cdi;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class CdiInstrumentationTest {

    private ApplicationScopedClass applicationScopedClass;
    private SingletonScopedClass singletonScopedClass;
    private NonAnnotatedClass nonAnnotatedClass;


    private MockReporter reporter;
    private ElasticApmTracer tracer;
    private CdiConfiguration cdiConfiguration;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        applicationScopedClass = new ApplicationScopedClass();
        singletonScopedClass = new SingletonScopedClass();
        nonAnnotatedClass = new NonAnnotatedClass();

        reporter = new MockReporter();
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        cdiConfiguration = config.getConfig(CdiConfiguration.class);
        Set<String> tags = testInfo.getTags();
        if (!tags.isEmpty()) {
            when(cdiConfiguration.getTraceMethodsDurationThreshold()).thenReturn(TimeDuration.of(tags.iterator().next()));
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
    void testNormalScopedIsInstrumented() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null);
        try (Scope scope = transaction.activateInScope()) {
            applicationScopedClass.doSomething();
        } finally {
            transaction.end();
        }
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span firstSpan = spans.get(0);
        assertThat(firstSpan.getName().toString()).isEqualTo("ApplicationScopedClass#doSomething");
        assertThat(firstSpan.getType()).isEqualTo("cdi");
    }

    @Test
    void testScopedIsInstrumented() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null);
        try (Scope scope = transaction.activateInScope()) {
            singletonScopedClass.doSomething();
        } finally {
            transaction.end();
        }
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        Span firstSpan = spans.get(0);
        assertThat(firstSpan.getName().toString()).isEqualTo("SingletonScopedClass#doSomething");
        assertThat(firstSpan.getType()).isEqualTo("cdi");
    }


    @Test
    void testNonAnnotatedClassIsNotInstrumented() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null);
        try (Scope scope = transaction.activateInScope()) {
            nonAnnotatedClass.doSomething();
        } finally {
            transaction.end();
        }
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(0);
    }

    @Test
    void testFilterConstructorsAndNonPublic() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null);
        try (Scope scope = transaction.activateInScope()) {
            ComplexClass complexClass = new ComplexClass(applicationScopedClass);
            complexClass.doSomething();
        } finally {
            transaction.end();
        }
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(2);
        Span firstSpan = spans.get(0);
        Span secondSpan = spans.get(1);
        assertThat(firstSpan.getName().toString()).isEqualTo("ComplexClass#internalPublicMethod");
        assertThat(secondSpan.getName().toString()).isEqualTo("ComplexClass#doSomething");
        assertThat(firstSpan.isChildOf(secondSpan)).isTrue();
    }

    @Test
    void testFilterCommonMethods() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null);
        try (Scope scope = transaction.activateInScope()) {
            ComplexClass complexClass = new ComplexClass(applicationScopedClass);
            complexClass.setInternalState("test");
            complexClass.getInternalState();
            complexClass.equals(complexClass);
            complexClass.hashCode();
            complexClass.toString();
        } finally {
            transaction.end();
        }
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(2);
        Span firstSpan = spans.get(0);
        Span secondSpan = spans.get(1);
        assertThat(firstSpan.getName().toString()).isEqualTo("ComplexClass#setInternalState");
        assertThat(secondSpan.getName().toString()).isEqualTo("ComplexClass#getInternalState");
    }


    @Test
    @Tag("10ms")
    void testThreshold() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null);
        try (Scope scope = transaction.activateInScope()) {
            applicationScopedClass.doSomethingFast();
            applicationScopedClass.doSomethingSlow();
            applicationScopedClass.callSomethingFast();
            applicationScopedClass.callSomethingSlow();
        } finally {
            transaction.end();
        }
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(3);
        Span firstSpan = spans.get(0);
        Span secondSpan = spans.get(1);
        Span thirdSpan = spans.get(2);
        assertThat(firstSpan.getName().toString()).isEqualTo("ApplicationScopedClass#doSomethingSlow");
        assertThat(firstSpan.getType()).isEqualTo("cdi");
        assertThat(secondSpan.getName().toString()).isEqualTo("ApplicationScopedClass#doSomethingSlow");
        assertThat(secondSpan.getType()).isEqualTo("cdi");
        assertThat(thirdSpan.getName().toString()).isEqualTo("ApplicationScopedClass#callSomethingSlow");
        assertThat(thirdSpan.getType()).isEqualTo("cdi");

        assertThat(secondSpan.isChildOf(thirdSpan)).isTrue();
    }

    @ApplicationScoped
    public static class ApplicationScopedClass {

        public void doSomething() {
            doSomethingInternal();
        }

        private void doSomethingInternal() {
        }


        public void doSomethingFast() {
        }

        public void callSomethingFast() {
            doSomethingFast();
        }

        public void callSomethingSlow() {
            doSomethingSlow();
        }

        public void doSomethingSlow() {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Sleep was interrupted");
            }
        }
    }

    @Singleton
    public static class SingletonScopedClass {
        public void doSomething() {

        }
    }

    public static class NonAnnotatedClass {


        public void doSomething() {

        }
    }

    @ApplicationScoped
    public static class ComplexClass {

        private ApplicationScopedClass applicationScopedClass;

        private String internalState;

        @Inject
        public ComplexClass(ApplicationScopedClass applicationScopedClass) {
            this.applicationScopedClass = applicationScopedClass;
        }

        public void doSomething() {
            protectedMethod();
            packagePrivateMethod();
            internalPublicMethod();
            internalPrivateMethod();
        }

        public void recurse(boolean recurse) {
            if (recurse) {
                recurse(false);
            }
        }

        protected void protectedMethod() {

        }

        void packagePrivateMethod() {

        }

        public void internalPublicMethod() {

        }

        private void internalPrivateMethod() {

        }

        public String getInternalState() {
            return internalState;
        }

        public void setInternalState(String internalState) {
            this.internalState = internalState;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ComplexClass that = (ComplexClass) o;
            return Objects.equals(internalState, that.internalState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(internalState);
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                .append("internalState", internalState)
                .toString();
        }
    }

}
