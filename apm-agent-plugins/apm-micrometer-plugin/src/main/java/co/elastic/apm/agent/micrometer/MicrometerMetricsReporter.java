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
package co.elastic.apm.agent.micrometer;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class MicrometerMetricsReporter implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MicrometerMetricsReporter.class);

    private final WeakConcurrentSet<MeterRegistry> meterRegistries = WeakMapSupplier.createSet();
    private final MicrometerMeterRegistrySerializer serializer = new MicrometerMeterRegistrySerializer();
    private final Reporter reporter;
    private final ElasticApmTracer tracer;
    private boolean scheduledReporting = false;

    public MicrometerMetricsReporter(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.reporter = tracer.getReporter();
    }

    public void registerMeterRegistry(MeterRegistry meterRegistry) {
        if (meterRegistry instanceof CompositeMeterRegistry) {
            return;
        }
        boolean added = meterRegistries.add(meterRegistry);
        if (added) {
            logger.info("Registering Micrometer MeterRegistry: {}", meterRegistry);
            scheduleReporting();
        }
    }

    private synchronized void scheduleReporting() {
        if (scheduledReporting) {
            return;
        }
        scheduledReporting = true;
        long metricsIntervalMs = tracer.getConfig(ReporterConfiguration.class).getMetricsIntervalMs();
        if (metricsIntervalMs > 0) {
            // called for every class loader that loaded micrometer
            // that's because a new MicrometerMetricsReporter instance is created in every IndyPluginClassLoader
            // for example if multiple webapps use potentially different versions of Micrometer
            tracer.getSharedSingleThreadedPool().scheduleAtFixedRate(this, metricsIntervalMs, metricsIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    // guaranteed to be invoked by a single thread
    @Override
    public void run() {
        if (tracer.getState() != Tracer.TracerState.RUNNING) {
            return;
        }
        final long timestamp = System.currentTimeMillis() * 1000;
        MeterMapConsumer meterConsumer = new MeterMapConsumer();
        for (MeterRegistry registry : meterRegistries) {
            registry.forEachMeter(meterConsumer);
        }
        logger.debug("Reporting {} meters", meterConsumer.meters.size());
        reporter.report(serializer.serialize(meterConsumer.meters, timestamp));
    }

    private static class MeterMapConsumer implements Consumer<Meter> {
        final Map<Meter.Id, Meter> meters = new HashMap<>();

        @Override
        public void accept(Meter meter) {
            meters.put(meter.getId(), meter);
        }
    }

    WeakConcurrentSet<MeterRegistry> getMeterRegistries() {
        return meterRegistries;
    }
}
