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
package co.elastic.apm.agent.embeddedotel;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyMeterProvider;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

/**
 * This class is loaded by the agent-classloader.
 * The actual embedded SDK is not directly accessible for instrumentations: io.opentelemetry.* is
 * blocked by the {@link co.elastic.apm.agent.bci.classloading.IndyPluginClassLoader}.
 * The SDK needs to be accessed via the proxy classes (e.g. {@link ProxyMeterProvider}) instead.
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
            sdkInstance.shutdown().join(5, TimeUnit.SECONDS);
        }
    }

    @Nullable
    public ProxyMeterProvider getMeterProvider() {
        if (sdkInstance == null) {
            startSdk();
        }
        return new ProxyMeterProvider(sdkInstance);
    }

    /**
     * The agent does prevent the accidental startup of a new SDK after it has been shutdown.
     * However, in tests this is required, therefore tests can call this method after stop() to allow reinitialization.
     */
    synchronized void reset() {
        sdkInstance = null;
        isShutdown = false;
    }

    private synchronized void startSdk() {
        if (isShutdown || sdkInstance != null || tracer == null) {
            return;
        }
        logger.debug("Starting embedded OpenTelemetry metrics SDK");
        SdkMeterProviderBuilder sdkBuilder = SdkMeterProvider.builder();
        //No need to register an exporter -> our metricsdk instrumentation will do that
        sdkInstance = sdkBuilder.build();
    }

}
