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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.Timer;
import co.elastic.apm.agent.report.serialize.MetricRegistrySerializer;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

class SpanTypeBreakdownTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        tracer = MockTracer.createRealTracer(reporter);
    }

    @AfterEach
    void tearDown() {
        final JsonWriter jw = new DslJson<>().newWriter();
        MetricRegistrySerializer.serialize(tracer.getMetricRegistry(), new StringBuilder(), jw);
        System.out.println(jw.toString());
    }

    /*
     * ██████████████████████████████
     *          10        20        30
     */
    @Test
    void testBreakdown_noSpans() {
        tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request")
            .end(30);
        assertThat(getTimer("span.self_time", "app").getCount()).isEqualTo(1);
        assertThat(getTimer("span.self_time", "app").getTotalTimeUs()).isEqualTo(30);
        assertThat(getTimer("transaction.duration", null).getTotalTimeUs()).isEqualTo(30);
        assertThatTransactionBreakdownCounterCreated();
    }

    /*
     * ██████████░░░░░░░░░░██████████
     * └─────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_singleDbSpan() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        transaction.createSpan(10).withType("db").end(20);
        transaction.end(30);

        assertThat(getTimer("span.self_time", "app").getCount()).isEqualTo(1);
        assertThat(getTimer("span.self_time", "app").getTotalTimeUs()).isEqualTo(20);
        assertThat(getTimer("transaction.duration", null).getTotalTimeUs()).isEqualTo(30);
        assertThatTransactionBreakdownCounterCreated();
        assertThat(getTimer("span.self_time", "db").getCount()).isEqualTo(1);
        assertThat(getTimer("span.self_time", "db").getTotalTimeUs()).isEqualTo(10);
    }

    /*
     * ██████████░░░░░░░░░░██████████
     * └─────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_singleAppSpan() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        transaction.createSpan(10).withType("app").end(20);
        transaction.end(30);

        assertThat(getTimer("span.self_time", "app").getCount()).isEqualTo(2);
        assertThat(getTimer("span.self_time", "app").getTotalTimeUs()).isEqualTo(30);
        assertThat(getTimer("transaction.duration", null).getTotalTimeUs()).isEqualTo(30);
        assertThatTransactionBreakdownCounterCreated();
    }

    /*
     * ██████████░░░░░░░░░░██████████
     * ├─────────██████████
     * └─────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_concurrentDbSpans_fullyOverlapping() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Span span1 = transaction.createSpan(10).withType("db");
        final Span span2 = transaction.createSpan(10).withType("db");
        span1.end(20);
        span2.end(20);
        transaction.end(30);

        assertThat(getTimer("span.self_time", "app").getCount()).isEqualTo(1);
        assertThat(getTimer("span.self_time", "app").getTotalTimeUs()).isEqualTo(20);
        assertThat(getTimer("transaction.duration", null).getTotalTimeUs()).isEqualTo(30);
        assertThatTransactionBreakdownCounterCreated();
        assertThat(getTimer("span.self_time", "db").getCount()).isEqualTo(2);
        assertThat(getTimer("span.self_time", "db").getTotalTimeUs()).isEqualTo(20);
    }

    /*
     * ██████████░░░░░░░░░░░░░░░█████
     * ├─────────██████████
     * └──────────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_concurrentDbSpans_partiallyOverlapping() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Span span1 = transaction.createSpan(10).withType("db");
        final Span span2 = transaction.createSpan(15).withType("db");
        span1.end(20);
        span2.end(25);
        transaction.end(30);

        assertThat(getTimer("span.self_time", "app").getCount()).isEqualTo(1);
        assertThat(getTimer("span.self_time", "app").getTotalTimeUs()).isEqualTo(15);
        assertThat(getTimer("transaction.duration", null).getTotalTimeUs()).isEqualTo(30);
        assertThatTransactionBreakdownCounterCreated();
        assertThat(getTimer("span.self_time", "db").getCount()).isEqualTo(2);
        assertThat(getTimer("span.self_time", "db").getTotalTimeUs()).isEqualTo(20);
    }

    /*
     * █████░░░░░░░░░░░░░░░░░░░░█████
     * ├────██████████
     * └──────────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_serialDbSpans_notOverlapping_withoutGap() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        transaction.createSpan(5).withType("db").end(15);
        transaction.createSpan(15).withType("db").end(25);
        transaction.end(30);

        assertThat(getTimer("span.self_time", "app").getCount()).isEqualTo(1);
        assertThat(getTimer("span.self_time", "app").getTotalTimeUs()).isEqualTo(10);
        assertThat(getTimer("transaction.duration", null).getTotalTimeUs()).isEqualTo(30);
        assertThatTransactionBreakdownCounterCreated();
        assertThat(getTimer("span.self_time", "db").getCount()).isEqualTo(2);
        assertThat(getTimer("span.self_time", "db").getTotalTimeUs()).isEqualTo(20);
    }

    /*
     * ██████████░░░░░█████░░░░░█████
     * ├─────────█████
     * └───────────────────█████
     *          10        20        30
     */
    @Test
    void testBreakdown_serialDbSpans_notOverlapping_withGap() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        transaction.createSpan(10).withType("db").end(15);
        transaction.createSpan(20).withType("db").end(25);
        transaction.end(30);

        assertThat(getTimer("span.self_time", "app").getCount()).isEqualTo(1);
        assertThat(getTimer("span.self_time", "app").getTotalTimeUs()).isEqualTo(20);
        assertThat(getTimer("transaction.duration", null).getTotalTimeUs()).isEqualTo(30);
        assertThatTransactionBreakdownCounterCreated();
        assertThat(getTimer("span.self_time", "db").getCount()).isEqualTo(2);
        assertThat(getTimer("span.self_time", "db").getTotalTimeUs()).isEqualTo(10);
    }

    /*
     * ██████████░░░░░░░░░░██████████
     * └─────────█████░░░░░ <- all child timers are force-stopped when a span finishes
     *           └────██████████      <- does not influence the transaction's self-time as it's not a direct child
     *          10        20        30
     */
    @Test
    void testBreakdown_asyncGrandchildExceedsChild() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Span app = transaction.createSpan(10).withType("app");
        final Span db = app.createSpan(15).withType("db");
        app.end(20);
        db.end(25);
        transaction.end(30);

        assertThat(getTimer("span.self_time", "app").getCount()).isEqualTo(2);
        assertThat(getTimer("span.self_time", "app").getTotalTimeUs()).isEqualTo(25);
        assertThat(getTimer("transaction.duration", null).getTotalTimeUs()).isEqualTo(30);
        assertThatTransactionBreakdownCounterCreated();
        assertThat(getTimer("span.self_time", "db").getCount()).isEqualTo(1);
        assertThat(getTimer("span.self_time", "db").getTotalTimeUs()).isEqualTo(10);
    }

    /*
     * breakdowns are reported when the transaction ends
     * any spans which outlive the transaction are not included in the breakdown
     *                    v
     * ██████████░░░░░░░░░░
     * └─────────██████████░░░░░░░░░░
     *           └─────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_asyncGrandchildExceedsChildAndTransaction() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Span app = transaction.createSpan(10).withType("app");
        transaction.end(20);
        reporter.decrementReferences();
        final Span db = app.createSpan(20).withType("db");
        app.end(30);
        db.end(30);

        assertThat(transaction.getSelfDuration()).isEqualTo(10);
        assertThat(app.getSelfDuration()).isEqualTo(10);
        assertThat(db.getSelfDuration()).isEqualTo(10);

        reporter.decrementReferences();
        assertThat(transaction.isReferenced()).isFalse();
        assertThat(app.isReferenced()).isFalse();
        assertThat(db.isReferenced()).isFalse();

        assertThat(getTimer("span.self_time", "app").getCount()).isEqualTo(1);
        assertThat(getTimer("span.self_time", "app").getTotalTimeUs()).isEqualTo(10);
        assertThat(getTimer("transaction.duration", null).getTotalTimeUs()).isEqualTo(20);
        assertThatTransactionBreakdownCounterCreated();
        assertThat(getTimer("span.self_time", "db").getCount()).isEqualTo(0);
    }

    /*
     * breakdowns are reported when the transaction ends
     * any spans which outlive the transaction are not included in the breakdown
     *                    v
     * ██████████░░░░░░░░░░
     * └─────────████████████████████
     *          10        20        30
     */
    @Test
    void testBreakdown_singleDbSpan_exceedingParent() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Span span = transaction.createSpan(10).withType("db");
        transaction.end(20);
        span.end(30);

        // recycled transactions should not leak child timings
        reporter.assertRecycledAfterDecrementingReferences();
        assertThat(reporter.getFirstTransaction().getSpanTimings().get("db")).isNull();

        assertThat(getTimer("span.self_time", "app").getCount()).isEqualTo(1);
        assertThat(getTimer("span.self_time", "app").getTotalTimeUs()).isEqualTo(10);
        assertThat(getTimer("transaction.duration", null).getTotalTimeUs()).isEqualTo(20);
        assertThatTransactionBreakdownCounterCreated();
        assertThat(getTimer("span.self_time", "db").getCount()).isEqualTo(0);
    }

    /*
     * breakdowns are reported when the transaction ends
     * any spans which outlive the transaction are not included in the breakdown
     *          v
     * ██████████
     * └───────────────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_spanStartedAfterParentEnded() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Runnable runnable = transaction.withActive(() -> {
            final TraceContextHolder<?> active = tracer.getActive();
            assertThat(active).isSameAs(transaction);
            assertThat(transaction.getTraceContext().getId().isEmpty()).isFalse();
            active.createSpan(20).withType("db").end(30);
        });
        transaction.end(10);
        runnable.run();

        reporter.assertRecycledAfterDecrementingReferences();

        assertThat(getTimer("span.self_time", "app").getCount()).isEqualTo(1);
        assertThat(getTimer("span.self_time", "app").getTotalTimeUs()).isEqualTo(10);
        assertThat(getTimer("transaction.duration", null).getTotalTimeUs()).isEqualTo(10);
        assertThatTransactionBreakdownCounterCreated();
        assertThat(getTimer("span.self_time", "db").getCount()).isEqualTo(0);
    }

    private void assertThatTransactionBreakdownCounterCreated() {
        assertThat(tracer.getMetricRegistry().getCount("transaction.breakdown.count", Labels.Mutable.of().transactionName("test").transactionType("request"))).isEqualTo(1);
    }

    @Nonnull
    private Timer getTimer(String timerName, @Nullable String spanType) {
        return tracer.getMetricRegistry().timer(timerName, Labels.Mutable.of().transactionName("test").transactionType("request").spanType(spanType));
    }
}
