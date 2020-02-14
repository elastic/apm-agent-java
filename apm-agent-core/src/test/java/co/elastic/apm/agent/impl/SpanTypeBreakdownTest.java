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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricSet;
import co.elastic.apm.agent.metrics.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.source.SimpleSource;

import javax.annotation.Nullable;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SuppressWarnings("ConstantConditions")
class SpanTypeBreakdownTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        tracer = MockTracer.createRealTracer(reporter);
    }

    @AfterEach
    void cleanup() {
        // some extra checks might be done when tracer is being stopped
        tracer.stop();
    }

    /*
     * ██████████████████████████████
     *          10        20        30
     */
    @Test
    void testBreakdown_noSpans() {
        tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request")
            .end(30);
        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getCount()).isEqualTo(1);
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getTotalTimeUs()).isEqualTo(30);
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(30);
            assertThatTransactionBreakdownCounterCreated(metricSets);
        });
    }

    /*
     * ██████████████████████████████
     *          10        20        30
     */
    @Test
    void testBreakdown_disabled() {
        when(tracer.getConfig(CoreConfiguration.class).isBreakdownMetricsEnabled()).thenReturn(false);
        tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request")
            .end(30);
        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null)).isNull();
            assertThat(getTimer(metricSets, "span.self_time", "app", null)).isNull();
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(30);
            assertThat(metricSets.get(Labels.Mutable.of().transactionName("test").transactionType("request")).getCounters().get("transaction.breakdown.count")).isNull();
        });
    }

    /*
     * ██████████░░░░░░░░░░██████████
     * └─────────██████████
     *          10        20        30g
     */
    @Test
    void testBreakdown_singleDbSpan() {
        final Transaction transaction = tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        transaction.createSpan(10).withType("db").withSubtype("mysql").end(20);
        transaction.end(30);

        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getCount()).isEqualTo(1);
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getTotalTimeUs()).isEqualTo(20);
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(30);
            assertThatTransactionBreakdownCounterCreated(metricSets);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getCount()).isEqualTo(1);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getTotalTimeUs()).isEqualTo(10);
        });
    }

    /*
     * ██████████░░░░░░░░░░██████████
     * └─────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_singleDbSpan_breakdownMetricsDisabled() {
        tracer = MockTracer.createRealTracer(reporter, SpyConfiguration.createSpyConfig(SimpleSource.forTest("disable_metrics", "span.self_time")));
        final Transaction transaction = tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        transaction.createSpan(10).withType("db").withSubtype("mysql").end(20);
        transaction.end(30);

        assertThat(transaction.getTimerBySpanTypeAndSubtype()).isEmpty();
        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null)).isNull();
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql")).isNull();
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(30);
        });
    }

    /*
     * ██████████░░░░░░░░░░██████████
     * └─────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_singleAppSpan() {
        final Transaction transaction = tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        transaction.createSpan(10).withType("app").end(20);
        transaction.end(30);

        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getCount()).isEqualTo(2);
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getTotalTimeUs()).isEqualTo(30);
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(30);
            assertThatTransactionBreakdownCounterCreated(metricSets);
        });
    }

    /*
     * ██████████░░░░░░░░░░██████████
     * ├─────────██████████
     * └─────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_concurrentDbSpans_fullyOverlapping() {
        final Transaction transaction = tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Span span1 = transaction.createSpan(10).withType("db").withSubtype("mysql");
        final Span span2 = transaction.createSpan(10).withType("db").withSubtype("mysql");
        span1.end(20);
        span2.end(20);
        transaction.end(30);

        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getCount()).isEqualTo(1);
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getTotalTimeUs()).isEqualTo(20);
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(30);
            assertThatTransactionBreakdownCounterCreated(metricSets);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getCount()).isEqualTo(2);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getTotalTimeUs()).isEqualTo(20);
        });
    }

    /*
     * ██████████░░░░░░░░░░░░░░░█████
     * ├─────────██████████
     * └──────────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_concurrentDbSpans_partiallyOverlapping() {
        final Transaction transaction = tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Span span1 = transaction.createSpan(10).withType("db").withSubtype("mysql");
        final Span span2 = transaction.createSpan(15).withType("db").withSubtype("mysql");
        span1.end(20);
        span2.end(25);
        transaction.end(30);

        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getCount()).isEqualTo(1);
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getTotalTimeUs()).isEqualTo(15);
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(30);
            assertThatTransactionBreakdownCounterCreated(metricSets);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getCount()).isEqualTo(2);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getTotalTimeUs()).isEqualTo(20);
        });
    }

    /*
     * █████░░░░░░░░░░░░░░░░░░░░█████
     * ├────██████████
     * └──────────────██████████
     *          10        20        30
     */
    @Test
    void testBreakdown_serialDbSpans_notOverlapping_withoutGap() {
        final Transaction transaction = tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        transaction.createSpan(5).withType("db").withSubtype("mysql").end(15);
        transaction.createSpan(15).withType("db").withSubtype("mysql").end(25);
        transaction.end(30);

        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getCount()).isEqualTo(1);
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getTotalTimeUs()).isEqualTo(10);
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(30);
            assertThatTransactionBreakdownCounterCreated(metricSets);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getCount()).isEqualTo(2);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getTotalTimeUs()).isEqualTo(20);
        });
    }

    /*
     * ██████████░░░░░█████░░░░░█████
     * ├─────────█████
     * └───────────────────█████
     *          10        20        30
     */
    @Test
    void testBreakdown_serialDbSpans_notOverlapping_withGap() {
        final Transaction transaction = tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        transaction.createSpan(10).withType("db").withSubtype("mysql").end(15);
        transaction.createSpan(20).withType("db").withSubtype("mysql").end(25);
        transaction.end(30);

        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getCount()).isEqualTo(1);
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getTotalTimeUs()).isEqualTo(20);
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(30);
            assertThatTransactionBreakdownCounterCreated(metricSets);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getCount()).isEqualTo(2);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getTotalTimeUs()).isEqualTo(10);
        });
    }

    /*
     * ██████████░░░░░░░░░░██████████
     * └─────────█████░░░░░ <- all child timers are force-stopped when a span finishes
     *           └────██████████      <- does not influence the transaction's self-time as it's not a direct child
     *          10        20        30
     */
    @Test
    void testBreakdown_asyncGrandchildExceedsChild() {
        final Transaction transaction = tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Span app = transaction.createSpan(10).withType("app");
        final Span db = app.createSpan(15).withType("db").withSubtype("mysql");
        app.end(20);
        db.end(25);
        transaction.end(30);

        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getCount()).isEqualTo(2);
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getTotalTimeUs()).isEqualTo(25);
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(30);
            assertThatTransactionBreakdownCounterCreated(metricSets);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getCount()).isEqualTo(1);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql").getTotalTimeUs()).isEqualTo(10);
        });
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
        final Transaction transaction = tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Span app = transaction.createSpan(10).withType("app");
        transaction.end(20);
        reporter.decrementReferences();
        final Span db = app.createSpan(20).withType("db").withSubtype("mysql");
        app.end(30);
        db.end(30);

        assertThat(transaction.getSelfDuration()).isEqualTo(10);
        assertThat(app.getSelfDuration()).isEqualTo(10);
        assertThat(db.getSelfDuration()).isEqualTo(10);

        reporter.decrementReferences();
        assertThat(transaction.isReferenced()).isFalse();
        assertThat(app.isReferenced()).isFalse();
        assertThat(db.isReferenced()).isFalse();

        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getCount()).isEqualTo(1);
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getTotalTimeUs()).isEqualTo(10);
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(20);
            assertThatTransactionBreakdownCounterCreated(metricSets);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql")).isNull();
        });
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
        final Transaction transaction = tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Span span = transaction.createSpan(10).withType("db").withSubtype("mysql");
        transaction.end(20);
        span.end(30);

        // recycled transactions should not leak child timings
        reporter.assertRecycledAfterDecrementingReferences();
        assertThat(reporter.getFirstTransaction().getTimerBySpanTypeAndSubtype().get("db")).isNull();

        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getCount()).isEqualTo(1);
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getTotalTimeUs()).isEqualTo(10);
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(20);
            assertThatTransactionBreakdownCounterCreated(metricSets);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql")).isNull();
        });
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
        final Transaction transaction = tracer.startRootTransaction(ConstantSampler.of(true), 0, getClass().getClassLoader())
            .withName("test")
            .withType("request");
        final Runnable runnable = transaction.withActive(() -> {
            final TraceContextHolder<?> active = tracer.getActive();
            assertThat(active).isSameAs(transaction);
            assertThat(transaction.getTraceContext().getId().isEmpty()).isFalse();
            active.createSpan(20).withType("db").withSubtype("mysql").end(30);
        });
        transaction.end(10);
        runnable.run();

        reporter.assertRecycledAfterDecrementingReferences();

        tracer.getMetricRegistry().report(metricSets -> {
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getCount()).isEqualTo(1);
            assertThat(getTimer(metricSets, "span.self_time", "app", null).getTotalTimeUs()).isEqualTo(10);
            assertThat(getTimer(metricSets, "transaction.duration", null, null).getTotalTimeUs()).isEqualTo(10);
            assertThatTransactionBreakdownCounterCreated(metricSets);
            assertThat(getTimer(metricSets, "span.self_time", "db", "mysql")).isNull();
        });
    }

    private void assertThatTransactionBreakdownCounterCreated(Map<? extends Labels, MetricSet> metricSets) {
        assertThat(metricSets.get(Labels.Mutable.of().transactionName("test").transactionType("request")).getCounters().get("transaction.breakdown.count").get()).isEqualTo(1);
    }

    @Nullable
    private Timer getTimer(Map<? extends Labels, MetricSet> metricSets, String timerName, @Nullable String spanType, @Nullable String spanSubType) {
        final MetricSet metricSet = metricSets.get(Labels.Mutable.of().transactionName("test").transactionType("request").spanType(spanType).spanSubType(spanSubType));
        if (metricSet == null) {
            return null;
        }
        return metricSet.timer(timerName);
    }
}
