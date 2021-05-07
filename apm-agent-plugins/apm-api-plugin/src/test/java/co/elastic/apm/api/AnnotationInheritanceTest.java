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

class AnnotationInheritanceTest {

    protected static ElasticApmTracer tracer;
    protected static MockReporter reporter;

    @BeforeAll
    public static synchronized void beforeAll() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        tracer = mockInstrumentationSetup.getTracer();
        when(tracer.getConfig(CoreConfiguration.class).isEnablePublicapiAnnotationInheritance()).thenReturn(true);
        reporter = mockInstrumentationSetup.getReporter();

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @AfterAll
    public static synchronized void afterAll() {
        ElasticApmAgent.reset();
    }

    @AfterEach
    void afterEach() {
        reporter.resetWithoutRecycling();
    }

    @Test
    void testCaptureTransaction() {
        new ClassWithoutAnnotations().captureTransaction();

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("ClassWithoutAnnotations#captureTransaction");
        assertThat(reporter.getFirstTransaction().getType()).isEqualTo(Transaction.TYPE_REQUEST);
    }

    @Test
    void testCaptureSpan() {
        try (Scope scope = ElasticApm.startTransaction().activate()) {
            new ClassWithoutAnnotations().captureSpan();
        }

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("ClassWithoutAnnotations#captureSpan");
        assertThat(reporter.getFirstSpan().getType()).isEqualTo("app");
    }

    @Test
    void testTracedWithoutActiveTransaction() {
        new ClassWithoutAnnotations().traced();

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("ClassWithoutAnnotations#traced");
        assertThat(reporter.getFirstTransaction().getType()).isEqualTo(Transaction.TYPE_REQUEST);
    }

    @Test
    void testTracedWithActiveTransaction() {
        try (Scope scope = ElasticApm.startTransaction().activate()) {
            new ClassWithoutAnnotations().traced();
        }

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("ClassWithoutAnnotations#traced");
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
