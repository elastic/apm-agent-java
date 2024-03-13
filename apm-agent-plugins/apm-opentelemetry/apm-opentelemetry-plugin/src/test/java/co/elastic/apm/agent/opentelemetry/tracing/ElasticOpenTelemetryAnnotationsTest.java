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
package co.elastic.apm.agent.opentelemetry.tracing;

import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TraceStateImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.testutils.assertions.Assertions;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class ElasticOpenTelemetryAnnotationsTest extends AbstractOpenTelemetryTest {

    @Before
    public void before() {
        checkNoActiveContext();
    }

    @After
    public void after() {
        checkNoActiveContext();
    }

    @Test
    public void withSpanAnnotationTestWithMethodSignatureSpanName() {
        executeSpanInTransactionAndAssertTransaction((ignore) -> fooSpan());

        SpanImpl firstSpan = reporter.getFirstSpan();
        assertThat(firstSpan.getNameAsString()).isEqualTo("ElasticOpenTelemetryAnnotationsTest#fooSpan");
        assertThat(reporter.getFirstSpan().isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    @Test
    public void withSpanAnnotationTestWithSpanNameFromAnnotation() {
        executeSpanInTransactionAndAssertTransaction((ignore) -> barSpan());

        SpanImpl firstSpan = reporter.getFirstSpan();
        assertThat(firstSpan.getNameAsString()).isEqualTo("barSpan");
        assertThat(reporter.getFirstSpan().isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    @Test
    public void withSpanAnnotationSpanAttributes() {
        executeSpanInTransactionAndAssertTransaction((ignore) -> fooSpanWithAttrs("foobar", "objectAsString", 2073, 2.69));

        SpanImpl firstSpan = reporter.getFirstSpan();
        assertThat(firstSpan.getNameAsString()).isEqualTo("ElasticOpenTelemetryAnnotationsTest#fooSpanWithAttrs");
        assertThat(firstSpan.isChildOf(reporter.getFirstTransaction())).isTrue();
        assertThat(firstSpan.getOtelAttributes().get("attr1")).isEqualTo("foobar");
        assertThat(firstSpan.getOtelAttributes().get("attr2")).isEqualTo("objectAsString");
        assertThat(firstSpan.getOtelAttributes().get("count")).isNull();
    }


    private void executeSpanInTransactionAndAssertTransaction(Consumer<?> function) {
        Span transaction = otelTracer.spanBuilder("transaction")
            .startSpan();
        try (Scope scope = transaction.makeCurrent()) {
            function.accept(null);
        } finally {
            transaction.end();
        }

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        TransactionImpl reportedTransaction = reporter.getFirstTransaction();
        assertThat(reportedTransaction.getNameAsString()).isEqualTo("transaction");
    }

    @WithSpan(kind = SpanKind.CLIENT)
    protected void fooSpan() {
    }

    @WithSpan(kind = SpanKind.INTERNAL, value = "barSpan")
    protected void barSpan() {
    }


    @WithSpan
    protected void fooSpanWithAttrs(@SpanAttribute("attr1") String string,
                                    @TestAnnotation @SpanAttribute("attr2") Object object,
                                    @SpanAttribute Integer count,
                                    Double doubleVal) {
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TestAnnotation {

    }

    private void checkNoActiveContext() {
        Assertions.assertThat(tracer.currentContext())
            .describedAs("no active elastic context is expected")
            .satisfies(TraceStateImpl::isEmpty);
        assertThat(Context.current())
            .describedAs("no active otel context is expected")
            .isSameAs(Context.root())
            .isNotNull();
    }
}
