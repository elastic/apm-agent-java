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
package co.elastic.apm.agent.opentelemetry.global;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.OtelMetricsBridge;
import co.elastic.apm.agent.otelembeddedsdk.EmbeddedSdkManager;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;

public class ElasticOpenTelemetryWithMetrics extends ElasticOpenTelemetry {

    private static final Logger logger = LoggerFactory.getLogger(ElasticOpenTelemetryWithMetrics.class);
    private static final Class<?> NOOP_METERPROVIDER_CLASS = MeterProvider.noop().getClass();

    private final MeterProvider meterProvider;

    public ElasticOpenTelemetryWithMetrics(OpenTelemetry delegate, ElasticApmTracer tracer) {
        super(tracer);
        MeterProvider original = delegate.getMeterProvider();
        if (NOOP_METERPROVIDER_CLASS.isInstance(original)) {
            logger.debug("No user-provided global OpenTelemetry MetricProvider detected. The agent will provide it's own.");
            co.elastic.apm.agent.shaded.otel.api.metrics.MeterProvider shadedSdk =
                tracer.getLifecycleListener(EmbeddedSdkManager.class).getOrStartSdk();
            if (shadedSdk != null) {
                meterProvider = OtelMetricsBridge.create(shadedSdk);
            } else {
                logger.error("Could not start embedded OpenTelemetry metrics sdk");
                meterProvider = original;
            }
        } else {
            logger.debug("Detected user-provided global OpenTelemetry MetricProvider of type {}", original.getClass().getName());
            meterProvider = original;
        }
    }

    @Override
    public MeterProvider getMeterProvider() {
        return meterProvider;
    }
}
