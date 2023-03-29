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
package co.elastic.apm.agent.otelmetricsdk;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.MetricsConfiguration;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.util.AtomicDouble;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThatMetricSets;
import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleArrayKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.mockito.Mockito.doReturn;

public abstract class AbstractOtelMetricsTest extends AbstractInstrumentationTest {

    private ReporterConfiguration reporterConfig;

    /**
     * The meter provider is lazily initialized on first usage (when {@link #createMeter(String)} is called).
     * This allows tests to adjust configurations prior to the initialization.
     */
    @Nullable
    private MeterProvider meterProvider;

    @BeforeEach
    public void setup() {
        reporterConfig = tracer.getConfig(ReporterConfiguration.class);
        // we use explicit flush in tests instead of periodic reporting to prevent flakyness
        doReturn(1_000_000L).when(reporterConfig).getMetricsIntervalMs();
        meterProvider = null;
    }

    protected synchronized MeterProvider getMeterProvider() {
        if (meterProvider == null) {
            meterProvider = createOrLookupMeterProvider();
        }
        return meterProvider;
    }

    protected abstract MeterProvider createOrLookupMeterProvider();

    protected abstract Meter createMeter(String name);

    protected void verifyAdditionalExportersCalled() {
    }

    protected abstract void invokeSdkForceFlush();

    protected void resetReporterAndFlushMetrics() {
        reporter.reset();
        invokeSdkForceFlush();
        verifyAdditionalExportersCalled();
    }

    @Test
    public void testAttributeTypes() {
        Meter testMeter = createMeter("test");
        LongCounter counter = testMeter.counterBuilder("my_counter").build();

        Attributes attribs = Attributes.builder()
            .put(stringKey("string_attrib"), "foo")
            .put(doubleKey("double_attrib"), Double.MAX_VALUE)
            .put(longKey("long_attrib"), Long.MAX_VALUE)
            .put(booleanKey("bool_attrib"), false)
            .put(stringArrayKey("string_array_attrib"), List.of("foo", "bar"))
            .put(doubleArrayKey("double_array_attrib"), List.of(1.0, Double.MAX_VALUE))
            .put(longArrayKey("long_array_attrib"), List.of(1L, Long.MAX_VALUE))
            .put(booleanArrayKey("bool_array_attrib"), List.of(true, false))
            .build();

        counter.add(42, attribs);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .hasMetricsetWithLabelsSatisfying(Map.of(
                    "otel_instrumentation_scope_name", "test",
                    "string_attrib", "foo",
                    "double_attrib", Double.MAX_VALUE,
                    "long_attrib", Long.MAX_VALUE,
                    "bool_attrib", false
                ), metrics -> metrics
                    .hasMetricsCount(1)
                    .containsValueMetric("my_counter", 42)
            );
    }

    @Test
    public void testAttributeNameSanitization() {
        Meter testMeter = createMeter("test");
        LongCounter counter = testMeter.counterBuilder("my_counter").build();

        Attributes attributes = Attributes.of(stringKey("please*sanitize.me\""), "dont*sanitize.me\"");
        counter.add(42, attributes);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .hasMetricsetWithLabelsSatisfying(
                "otel_instrumentation_scope_name", "test",
                "please_sanitize_me_", "dont*sanitize.me\""
                , metrics -> metrics
                    .hasMetricsCount(1)
                    .containsValueMetric("my_counter", 42)
            );
    }

    @Test
    public void testTimestampPresent() {
        Meter testMeter = createMeter("test");
        LongCounter counter = testMeter.counterBuilder("my_counter").build();

        Instant instantBefore = Instant.now();
        long microsBefore = TimeUnit.SECONDS.toMicros(instantBefore.getEpochSecond()) + TimeUnit.NANOSECONDS.toMicros(instantBefore.getNano());

        counter.add(42);
        resetReporterAndFlushMetrics();

        Instant instantAfter = Instant.now();
        long microsAfter = TimeUnit.SECONDS.toMicros(instantAfter.getEpochSecond()) + TimeUnit.NANOSECONDS.toMicros(instantAfter.getNano());

        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            //check for a slightly bigger range due to potential clock differences
            .hasTimestampInRange(microsBefore - 10_000L, microsAfter + 10_000L);
    }

    @Test
    public void testMetricsCombinedInMetricsets() {
        Meter testMeter = createMeter("test");
        ObservableDoubleGauge gauge1 = testMeter
            .gaugeBuilder("my_gauge")
            .buildWithCallback((obs) -> {
                obs.record(42);
                obs.record(142, Attributes.of(stringKey("foo"), "bar"));
            });
        ObservableLongGauge gauge2 = testMeter
            .gaugeBuilder("my_other_gauge").ofLongs()
            .buildWithCallback((obs) -> {
                obs.record(43);
                obs.record(143, Attributes.of(stringKey("foo"), "bar"));
            });

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(2)
            .hasMetricsetWithLabelsSatisfying(
                "otel_instrumentation_scope_name", "test",
                metrics -> metrics.containsValueMetric("my_gauge", 42.0)
                    .containsValueMetric("my_other_gauge", 43)
                    .hasMetricsCount(2)
            )
            .hasMetricsetWithLabelsSatisfying(
                "otel_instrumentation_scope_name", "test",
                "foo", "bar",
                metrics -> metrics
                    .containsValueMetric("my_gauge", 142.0)
                    .containsValueMetric("my_other_gauge", 143)
                    .hasMetricsCount(2)
            );
    }

    @Test
    public void testSameMetricDifferentMeter() {
        Meter meter1 = createMeter("meter1");
        meter1.counterBuilder("my_counter").build().add(10);

        Meter meter2 = createMeter("meter2");
        meter2.counterBuilder("my_counter").build().add(20);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(2)
            .hasMetricsetWithLabelsSatisfying(
                "otel_instrumentation_scope_name", "meter1",
                metrics -> metrics
                    .containsValueMetric("my_counter", 10)
                    .hasMetricsCount(1)
            )
            .hasMetricsetWithLabelsSatisfying(
                "otel_instrumentation_scope_name", "meter2",
                metrics -> metrics
                    .containsValueMetric("my_counter", 20)
                    .hasMetricsCount(1)
            );
    }

    @Test
    public void testDedotSettingIgnored() {
        MetricsConfiguration config = tracer.getConfig(MetricsConfiguration.class);
        doReturn(true).when(config).isDedotCustomMetrics();

        Meter meter1 = createMeter("test");
        meter1.counterBuilder("foo.bar").build().add(10);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("foo.bar", 10)
            .hasMetricsCount(1);
    }

    @Test
    public void testMetricDisabling() {
        MetricsConfiguration config = tracer.getConfig(MetricsConfiguration.class);
        doReturn(List.of(
            WildcardMatcher.valueOf("metric.a")
        )).when(reporterConfig).getDisableMetrics();

        Meter meter1 = createMeter("test");
        meter1.counterBuilder("metric.a").build().add(10);
        meter1.counterBuilder("metric.b").build().add(20);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("metric.b", 20)
            .hasMetricsCount(1);
    }

    @Test
    public void testBatchObservation() {
        try {
            DoubleGaugeBuilder.class.getMethod("buildObserver");
        } catch (NoSuchMethodException expected) {
            //we are in an integration test where .buildObserver() doesn't exist, skip this test
            return;
        }
        // This runnable is required so that JUnit does not accidentally try to load the
        // potentially absent used types (e.g. ObservableDoubleMeasurement) while inspecting the test class
        new Runnable() {
            @Override
            public void run() {

                Meter testMeter = createMeter("test");

                ObservableDoubleMeasurement doubleGauge = testMeter
                    .gaugeBuilder("double_gauge")
                    .buildObserver();
                ObservableLongMeasurement longGauge = testMeter
                    .gaugeBuilder("long_gauge")
                    .ofLongs()
                    .buildObserver();
                ObservableDoubleMeasurement doubleCounter = testMeter
                    .counterBuilder("double_counter")
                    .ofDoubles()
                    .buildObserver();
                ObservableLongMeasurement longCounter = testMeter
                    .counterBuilder("long_counter")
                    .buildObserver();
                ObservableDoubleMeasurement doubleUpDownCounter = testMeter
                    .upDownCounterBuilder("double_updown_counter")
                    .ofDoubles()
                    .buildObserver();
                ObservableLongMeasurement longUpDownCounter = testMeter
                    .upDownCounterBuilder("long_updown_counter")
                    .buildObserver();

                testMeter.batchCallback(() -> {
                    doubleGauge.record(1.5);
                    longGauge.record(15);
                    doubleCounter.record(2.5);
                    longCounter.record(25);
                    doubleUpDownCounter.record(3.5);
                    longUpDownCounter.record(35);
                }, doubleGauge, longGauge, doubleCounter, longCounter, doubleUpDownCounter, longUpDownCounter);

                resetReporterAndFlushMetrics();

                assertThatMetricSets(reporter.getBytes())
                    .hasMetricsetCount(1)
                    .first()
                    .containsValueMetric("double_gauge", 1.5)
                    .containsValueMetric("long_gauge", 15)
                    .containsValueMetric("double_counter", 2.5)
                    .containsValueMetric("long_counter", 25)
                    .containsValueMetric("double_updown_counter", 3.5)
                    .containsValueMetric("long_updown_counter", 35)
                    .hasMetricsCount(6);
            }
        }.run();
    }

    @Test
    public void testObservableCounter() {
        Meter testMeter = createMeter("test");

        AtomicDouble doubleVal = new AtomicDouble();
        AtomicLong longVal = new AtomicLong();

        ObservableDoubleCounter doubleCnt = testMeter
            .counterBuilder("double_counter")
            .ofDoubles()
            .buildWithCallback((obs) -> {
                obs.record(doubleVal.get());
            });

        ObservableLongCounter longCnt = testMeter
            .counterBuilder("long_counter")
            .buildWithCallback((obs) -> {
                obs.record(longVal.get());
            });

        doubleVal.set(100.5);
        longVal.set(100);
        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", 100.5)
            .containsValueMetric("long_counter", 100)
            .hasMetricsCount(2);


        doubleVal.set(111);
        longVal.set(110);
        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", 10.5)
            .containsValueMetric("long_counter", 10)
            .hasMetricsCount(2);

        //unchanged counters are not exported
        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(0);
    }

    @Test
    public void testCounter() {
        Meter testMeter = createMeter("test");

        DoubleCounter doubleCnt = testMeter
            .counterBuilder("double_counter")
            .ofDoubles()
            .build();

        LongCounter longCnt = testMeter
            .counterBuilder("long_counter")
            .build();

        doubleCnt.add(1.5);
        longCnt.add(2);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", 1.5)
            .containsValueMetric("long_counter", 2)
            .hasMetricsCount(2);

        doubleCnt.add(2.5);
        doubleCnt.add(2);
        longCnt.add(3);
        longCnt.add(1);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", 4.5)
            .containsValueMetric("long_counter", 4)
            .hasMetricsCount(2);

        resetReporterAndFlushMetrics();
        //unchanged counters are not exported
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(0);
    }

    @Test
    public void testObservableUpDownCounter() {
        Meter testMeter = createMeter("test");

        AtomicDouble doubleVal = new AtomicDouble();
        AtomicLong longVal = new AtomicLong();

        ObservableDoubleUpDownCounter doubleCnt = testMeter
            .upDownCounterBuilder("double_counter")
            .ofDoubles()
            .buildWithCallback((obs) -> {
                obs.record(doubleVal.get());
            });

        ObservableLongUpDownCounter longCnt = testMeter
            .upDownCounterBuilder("long_counter")
            .buildWithCallback((obs) -> {
                obs.record(longVal.get());
            });

        doubleVal.set(100.5);
        longVal.set(100);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", 100.5)
            .containsValueMetric("long_counter", 100)
            .hasMetricsCount(2);

        doubleVal.set(0.0);
        longVal.set(0);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", 0.0)
            .containsValueMetric("long_counter", 0)
            .hasMetricsCount(2);

        doubleVal.set(90.2);
        longVal.set(42);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", 90.2)
            .containsValueMetric("long_counter", 42)
            .hasMetricsCount(2);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", 90.2)
            .containsValueMetric("long_counter", 42);
    }


    @Test
    public void testUpDownCounter() {
        Meter testMeter = createMeter("test");

        DoubleUpDownCounter doubleCnt = testMeter
            .upDownCounterBuilder("double_counter")
            .ofDoubles()
            .build();

        LongUpDownCounter longCnt = testMeter
            .upDownCounterBuilder("long_counter")
            .build();

        doubleCnt.add(1.5);
        longCnt.add(2);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", 1.5)
            .containsValueMetric("long_counter", 2)
            .hasMetricsCount(2);


        doubleCnt.add(-1.5);
        longCnt.add(-2);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", 0.0)
            .containsValueMetric("long_counter", 0)
            .hasMetricsCount(2);

        doubleCnt.add(-150);
        doubleCnt.add(49.5);
        longCnt.add(-10);
        longCnt.add(1);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", -100.5)
            .containsValueMetric("long_counter", -9)
            .hasMetricsCount(2);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsValueMetric("double_counter", -100.5)
            .containsValueMetric("long_counter", -9)
            .hasMetricsCount(2);
    }


    @Test
    public void testHistogram() {
        MetricsConfiguration metricsConfig = config.getConfig(MetricsConfiguration.class);
        doReturn(List.of(1.0, 5.0)).when(metricsConfig).getCustomMetricsHistogramBoundaries();

        Meter testMeter = createMeter("test");
        DoubleHistogram doubleHisto = testMeter.histogramBuilder("double_histo").build();
        LongHistogram longHisto = testMeter.histogramBuilder("long_histo").ofLongs().build();

        doubleHisto.record(0.5);
        doubleHisto.record(1.5);
        doubleHisto.record(1.5);
        doubleHisto.record(5.5);
        doubleHisto.record(5.5);
        doubleHisto.record(5.5);

        longHisto.record(0);
        longHisto.record(2);
        longHisto.record(2);
        longHisto.record(6);
        longHisto.record(6);
        longHisto.record(6);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsHistogramMetric("double_histo", List.of(0.5, 3.0, 5.0), List.of(1L, 2L, 3L))
            .containsHistogramMetric("long_histo", List.of(0.5, 3.0, 5.0), List.of(1L, 2L, 3L))
            .hasMetricsCount(2);

        //make sure only delta is reported and empty buckets are omitted
        doubleHisto.record(1.5);
        longHisto.record(2);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsHistogramMetric("double_histo", List.of(3.0), List.of(1L))
            .containsHistogramMetric("long_histo", List.of(3.0), List.of(1L))
            .hasMetricsCount(2);

        //empty histograms must not be exported
        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(0);
    }


    /**
     * Smoke test which just checks that nothing breaks with the default histogram buckets.
     */
    @Test
    public void testDefaultHistogramBuckets() {
        MetricsConfiguration metricsConfig = config.getConfig(MetricsConfiguration.class);
        List<Double> boundaries = metricsConfig.getCustomMetricsHistogramBoundaries();
        assertThat(boundaries).isNotEmpty();

        Meter testMeter = createMeter("test");
        DoubleHistogram doubleHisto = testMeter.histogramBuilder("double_histo").build();
        LongHistogram longHisto = testMeter.histogramBuilder("long_histo").ofLongs().build();

        long totalSum = 0l;
        for (int i = 1; i < 1000; i++) {
            double value = 0.00001d * Math.pow(1.1, i);
            for (int j = 0; j < i; j++) {
                totalSum++;
                longHisto.record(Math.round(value));
                doubleHisto.record(value);
            }
        }
        final long totalSumFinal = totalSum;

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .metricSatisfies("double_histo",
                metric -> assertThat(metric.counts.stream().mapToLong(Long::longValue).sum()).isEqualTo(totalSumFinal))
            .metricSatisfies("long_histo",
                metric -> assertThat(metric.counts.stream().mapToLong(Long::longValue).sum()).isEqualTo(totalSumFinal))
            .hasMetricsCount(2);
    }


}
