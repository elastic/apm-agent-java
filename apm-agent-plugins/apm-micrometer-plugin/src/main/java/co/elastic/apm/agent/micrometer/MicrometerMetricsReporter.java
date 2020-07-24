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
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

import java.util.concurrent.TimeUnit;

public class MicrometerMetricsReporter implements Runnable {

    private final WeakConcurrentSet<MeterRegistry> nonCompositeMeterRegistries = WeakMapSupplier.createSet();
    private final WeakConcurrentSet<CompositeMeterRegistry> compositeMeterRegistries = WeakMapSupplier.createSet();
    private final StringBuilder replaceBuilder = new StringBuilder();
    private final JsonWriter jsonWriter = new DslJson<>().newWriter();
    private final Reporter reporter;
    private final ElasticApmTracer tracer;
    private boolean scheduledReporting = false;

    public MicrometerMetricsReporter(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.reporter = tracer.getReporter();
    }

    public void registerMeterRegistry(MeterRegistry meterRegistry) {
        boolean added;
        if (meterRegistry instanceof CompositeMeterRegistry) {
            added = compositeMeterRegistries.add((CompositeMeterRegistry) meterRegistry);
        } else {
            added = nonCompositeMeterRegistries.add(meterRegistry);
        }
        if (added) {
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
            tracer.getSharedSingleThreadedPool().scheduleAtFixedRate(this, metricsIntervalMs, metricsIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    void clear() {
        nonCompositeMeterRegistries.clear();
    }

    // guaranteed to be invoked by a single thread
    @Override
    public void run() {
        if (tracer.getState() != Tracer.TracerState.RUNNING) {
            return;
        }
        final long timestamp = System.currentTimeMillis() * 1000;
        report(timestamp, nonCompositeMeterRegistries);
        report(timestamp, compositeMeterRegistries);
        if (jsonWriter.size() > 0) {
            reporter.report(jsonWriter.toByteArray());
            jsonWriter.reset();
        }
    }

    private void report(long timestamp, WeakConcurrentSet<? extends MeterRegistry> nonCompositeMeterRegistries) {
        for (MeterRegistry meterRegistry : nonCompositeMeterRegistries) {
            if (!isRegisteredInCompositeMeterRegistry(meterRegistry)) {
                MicrometerMeterRegistrySerializer.serialize(meterRegistry, timestamp, replaceBuilder, jsonWriter);
            }
        }
    }

    private boolean isRegisteredInCompositeMeterRegistry(MeterRegistry meterRegistry) {
        for (CompositeMeterRegistry compositeMeterRegistry : compositeMeterRegistries) {
            if (compositeMeterRegistry.getRegistries().contains(meterRegistry)) {
                return true;
            }
        }
        return false;
    }
}
