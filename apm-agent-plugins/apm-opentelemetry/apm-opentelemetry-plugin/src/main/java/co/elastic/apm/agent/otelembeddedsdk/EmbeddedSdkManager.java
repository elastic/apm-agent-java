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
package co.elastic.apm.agent.otelembeddedsdk;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.shaded.otel.api.metrics.MeterProvider;
import co.elastic.apm.agent.shaded.otel.sdk.metrics.SdkMeterProvider;
import co.elastic.apm.agent.shaded.otel.sdk.metrics.SdkMeterProviderBuilder;
import co.elastic.apm.agent.shaded.otelmetricexport.ElasticOtelMetricsExporter;

import javax.annotation.Nullable;

/**
 * This class resides in a different package to prevent it being loaded by the plugin classlaoder.
 * Manages a single, embedded Otel Metrics SDK instance which lives inside the agent classloader.
 */
public class EmbeddedSdkManager extends AbstractLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedSdkManager.class);

    @Nullable
    private ElasticApmTracer tracer;
    @Nullable
    private volatile SdkMeterProvider sdkInstance;

    private boolean isShutdown = false;

    @Override
    public synchronized void start(ElasticApmTracer tracer) throws Exception {
        this.tracer = tracer;
    }

    @Override
    public synchronized void stop() {
        isShutdown = true;
        if (sdkInstance != null) {
            logger.debug("Shutting down embedded OpenTelemetry metrics SDK");
            sdkInstance.shutdown();
        }
    }

    @Nullable
    public MeterProvider getOrStartSdk() {
        if (sdkInstance == null) {
            startSdk();
        }
        return sdkInstance;
    }

    public void resetForTests() {
        stop();
        sdkInstance = null;
        isShutdown = false;
    }

    private synchronized void startSdk() {
        if (isShutdown || sdkInstance != null || tracer == null) {
            return;
        }
        logger.debug("Starting embedded OpenTelemetry metrics SDK");
        SdkMeterProviderBuilder sdkBuilder = SdkMeterProvider.builder();
        ElasticOtelMetricsExporter.createAndRegisterOn(sdkBuilder, tracer);
        sdkInstance = sdkBuilder.build();
    }

}
