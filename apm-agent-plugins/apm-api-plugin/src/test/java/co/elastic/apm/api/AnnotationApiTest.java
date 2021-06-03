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
package co.elastic.apm.api;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.transaction.Span;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnotationApiTest extends AbstractInstrumentationTest {

    @Test
    void testCaptureTransactionAnnotation() {
        new AnnotationTestClass().transaction();

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("transaction");
        assertThat(reporter.getFirstTransaction().getContext().getLabel("foo")).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("API");

        assertThat(reporter.getSpans()).hasSize(2);

        assertThat(reporter.getSpans().get(0).getNameAsString()).isEqualTo("AnnotationTestClass#nestedSpan");
        assertThat(reporter.getSpans().get(0).getContext().getLabel("foo")).isEqualTo("bar");
        assertThat(reporter.getSpans().get(0).isChildOf(reporter.getSpans().get(1))).isTrue();

        assertThat(reporter.getSpans().get(1).getNameAsString()).isEqualTo("AnnotationTestClass#span");
        assertThat(reporter.getSpans().get(1).isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    @Test
    public void testAgentPaused_CaptureTransaction() {
        TracerInternalApiUtils.pauseTracer(tracer);
        int transactionCount = objectPoolFactory.getTransactionPool().getRequestedObjectCount();
        int spanCount = objectPoolFactory.getSpanPool().getRequestedObjectCount();
        new AnnotationTestClass().transaction();
        assertThat(reporter.getTransactions()).isEmpty();
        assertThat(reporter.getSpans()).isEmpty();
        assertThat(objectPoolFactory.getTransactionPool().getRequestedObjectCount()).isEqualTo(transactionCount);
        assertThat(objectPoolFactory.getSpanPool().getRequestedObjectCount()).isEqualTo(spanCount);
    }

    @Test
    void testCaptureTransactionAnnotationException() {
        assertThatThrownBy(AnnotationTestClass::transactionWithException).isInstanceOf(RuntimeException.class);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("AnnotationTestClass#transactionWithException");
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("API");
        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(reporter.getFirstError().getException().getMessage()).isEqualTo("catch me if you can");
    }

    @Test
    void testType() {
        testTransactionAndSpanTypes(true);
        testTransactionAndSpanTypes(false);
    }

    private void testTransactionAndSpanTypes(boolean useLegacyTyping) {

        reporter.disableCheckStrictSpanType();

        reporter.reset();
        AnnotationTestClass.transactionWithType(useLegacyTyping);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("transactionWithType");
        assertThat(reporter.getFirstTransaction().getType()).isEqualTo("job");
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("API");

        assertThat(reporter.getSpans()).hasSize(1);
        Span internalSpan = reporter.getFirstSpan();
        assertThat(internalSpan.getNameAsString()).isEqualTo("spanWithType");
        assertThat(internalSpan.getType()).isEqualTo("external");
        assertThat(internalSpan.getSubtype()).isEqualTo("http");
        assertThat(internalSpan.getAction()).isEqualTo("okhttp");
    }

    @Test
    void testMissingSubtype() {
        reporter.disableCheckStrictSpanType();

        AnnotationTestClass.transactionForMissingSpanSubtype();
        assertThat(reporter.getSpans()).hasSize(1);
        Span internalSpan = reporter.getFirstSpan();
        assertThat(internalSpan.getNameAsString()).isEqualTo("spanWithMissingSubtype");
        assertThat(internalSpan.getType()).isEqualTo("external.http");
        assertThat(internalSpan.getSubtype()).isNull();
        assertThat(internalSpan.getAction()).isEqualTo("okhttp");
    }

    @Test
    void testTracedTransactionAndSpan() {
        // type = app, subtype = subtype is not part of shared spec
        reporter.disableCheckStrictSpanType();

        TracedAnnotationTest.tracedTransaction();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);

        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("TracedAnnotationTest#tracedTransaction");
        assertThat(reporter.getFirstTransaction().getType()).isEqualTo("type");
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("API");

        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("TracedAnnotationTest#tracedSpanOrTransaction");
        assertThat(reporter.getFirstSpan().getType()).isEqualTo("app");
        assertThat(reporter.getFirstSpan().getSubtype()).isEqualTo("subtype");
        assertThat(reporter.getFirstSpan().getAction()).isEqualTo("action");
    }

    @Test
    public void testAgentPaused_TracedAnnotation() {
        TracerInternalApiUtils.pauseTracer(tracer);
        int transactionCount = objectPoolFactory.getTransactionPool().getRequestedObjectCount();
        int spanCount = objectPoolFactory.getSpanPool().getRequestedObjectCount();
        TracedAnnotationTest.tracedTransaction();
        assertThat(reporter.getTransactions()).isEmpty();
        assertThat(reporter.getSpans()).isEmpty();
        assertThat(objectPoolFactory.getTransactionPool().getRequestedObjectCount()).isEqualTo(transactionCount);
        assertThat(objectPoolFactory.getSpanPool().getRequestedObjectCount()).isEqualTo(spanCount);
    }

    @Test
    void testTracedTransaction() {
        TracedAnnotationTest.tracedSpanOrTransaction();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(0);

        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("TracedAnnotationTest#tracedSpanOrTransaction");
        assertThat(reporter.getFirstTransaction().getType()).isEqualTo("request");
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("API");
    }

    public static class AnnotationTestClass {

        @CaptureTransaction
        static void transactionWithException() {
            throw new RuntimeException("catch me if you can");
        }

        @CaptureTransaction("transaction")
        void transaction() {
            ElasticApm.currentTransaction().addLabel("foo", "bar");
            span();
        }

        @CaptureSpan
        void span() {
            nestedSpan();
        }

        @CaptureSpan
        void nestedSpan() {
            ElasticApm.currentSpan().addLabel("foo", "bar");
        }

        @CaptureTransaction(value = "transactionWithType", type = "job")
        static void transactionWithType(boolean useLegacyTyping) {
            if (useLegacyTyping) {
                spanWithHierarchicalType();
            } else {
                spanWithSplitTypes();
            }
        }

        @CaptureSpan(value = "spanWithType", type = "external.http.okhttp")
        private static void spanWithHierarchicalType() {

        }

        @CaptureSpan(value = "spanWithType", type = "external", subtype = "http", action = "okhttp")
        private static void spanWithSplitTypes() {

        }

        @CaptureTransaction()
        static void transactionForMissingSpanSubtype() {
            spanWithMissingSubtype();
        }

        @CaptureSpan(value = "spanWithMissingSubtype", type = "external.http", action = "okhttp")
        private static void spanWithMissingSubtype() {

        }
    }

    static class TracedAnnotationTest {
        @Traced(type = "type")
        static void tracedTransaction() {
            tracedSpanOrTransaction();
        }

        @Traced(subtype = "subtype", action = "action")
        static void tracedSpanOrTransaction() {
        }
    }
}
