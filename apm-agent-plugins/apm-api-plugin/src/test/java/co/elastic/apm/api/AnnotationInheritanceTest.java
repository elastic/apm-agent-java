/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2021 Elastic and contributors
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
package co.elastic.apm.api;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test annotation inheritance. Does not inherit from AbstractInstrumentationTest due to non-runtime configuration that
 * must be applied BEFORE agent instrumentation.
 */
class AnnotationInheritanceTest {

    protected static ElasticApmTracer tracer;
    protected static MockReporter reporter;

    @BeforeAll
    static void beforeAll() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        tracer = mockInstrumentationSetup.getTracer();
        when(tracer.getConfig(CoreConfiguration.class).isEnablePublicapiAnnotationInheritance()).thenReturn(true);
        reporter = mockInstrumentationSetup.getReporter();

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @AfterEach
    void cleanup() {
        reporter.reset();
    }

    @AfterAll
    static void afterAll() {
        ElasticApmAgent.reset();
    }

    @Test
    void testCaptureTransaction() {
        new ClassWithoutAnnotations().captureTransaction();

        checkTransaction("ClassWithoutAnnotations#captureTransaction");
    }

    @Test
    void testCaptureSpan() {
        try (Scope scope = ElasticApm.startTransaction().activate()) {
            new ClassWithoutAnnotations().captureSpan();
        }

        checkSpan("ClassWithoutAnnotations#captureSpan");
    }

    @Test
    void testTracedWithoutActiveTransaction() {
        new ClassWithoutAnnotations().traced();

        checkTransaction("ClassWithoutAnnotations#traced");
    }

    @Test
    void testTracedWithActiveTransaction() {
        try (Scope scope = ElasticApm.startTransaction().activate()) {
            new ClassWithoutAnnotations().traced();
        }

        checkSpan("ClassWithoutAnnotations#traced");
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

    static abstract class ClassWithAnnotations {
        @CaptureTransaction
        abstract void captureTransaction();

        @CaptureSpan
        abstract void captureSpan();

        @Traced
        abstract void traced();
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
}
