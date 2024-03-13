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

import co.elastic.apm.agent.configuration.MetricsConfigurationImpl;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThatMetricSets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class PrivateUserSdkOtelMetricsTest extends AbstractOtelMetricsTest {

    private MetricReader additionalReader;

    @Nullable
    private Consumer<SdkMeterProviderBuilder> sdkCustomizer;

    @Override
    protected MeterProvider createOrLookupMeterProvider() {
        additionalReader = Mockito.spy(new DummyMetricReader());

        SdkMeterProviderBuilder sdkMeterProviderBuilder = SdkMeterProvider.builder()
            .registerMetricReader(additionalReader);
        if (sdkCustomizer != null) {
            sdkCustomizer.accept(sdkMeterProviderBuilder);
        }
        return sdkMeterProviderBuilder.build();
    }

    @Override
    protected void verifyAdditionalExportersCalled() {
        verify(additionalReader).forceFlush();
        Mockito.reset(additionalReader);
    }

    @BeforeEach
    public void cleanSdkCustomizer() {
        sdkCustomizer = null;
    }

    @AfterEach
    public void cleanupUserSdk() {
        ((SdkMeterProvider) getMeterProvider()).shutdown().join(10, TimeUnit.SECONDS);
    }

    @Override
    protected Meter createMeter(String name) {
        Meter meter = getMeterProvider().get(name);
        try {
            //make sure that we use our own SDK classes instead of the ones provided by the agent
            assertThat(meter.getClass()).isSameAs(Class.forName("io.opentelemetry.sdk.metrics.SdkMeter"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return meter;
    }

    @Override
    protected void invokeSdkForceFlush() {
        ((SdkMeterProvider) getMeterProvider()).forceFlush();
    }


    @Test
    @Override
    public void testDefaultHistogramBuckets() {
        // Unfortunately default histogram buckets don't work with user-provided SDKs of version 1.32.0 or newer
        // The reason is that a default aggregation provided by the exporter would override
        // bucket boundaries set via DoubleHistogramBuilder.setExplicitBucketBoundaries
        // We decided to instead respect the bucket boundaries provided by the API
        try {
            DoubleHistogramBuilder.class.getMethod("setExplicitBucketBoundariesAdvice", List.class);
            //Method exists, default bucket boundaries are not supported
            //Don't execute test for that reason
        } catch (NoSuchMethodException e) {
            super.testDefaultHistogramBuckets();
        }
    }

    @Test
    public void testCustomHistogramView() {
        sdkCustomizer = builder -> builder.registerView(
            InstrumentSelector.builder().setName("custom_histo").build(),
            View.builder().setAggregation(Aggregation.explicitBucketHistogram(List.of(1.0, 5.0))).build()
        );

        MetricsConfigurationImpl metricsConfig = config.getConfig(MetricsConfigurationImpl.class);
        doReturn(List.of(42.0)).when(metricsConfig).getCustomMetricsHistogramBoundaries();

        Meter testMeter = createMeter("test");
        DoubleHistogram histo = testMeter.histogramBuilder("custom_histo").build();

        histo.record(0.5);
        histo.record(1.5);
        histo.record(1.5);
        histo.record(5.5);
        histo.record(5.5);
        histo.record(5.5);

        resetReporterAndFlushMetrics();
        assertThatMetricSets(reporter.getBytes())
            .hasMetricsetCount(1)
            .first()
            .containsHistogramMetric("custom_histo", List.of(0.5, 3.0, 5.0), List.of(1L, 2L, 3L))
            .hasMetricsCount(1);
    }

}
