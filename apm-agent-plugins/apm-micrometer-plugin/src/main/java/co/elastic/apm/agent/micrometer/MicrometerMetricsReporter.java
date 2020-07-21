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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MicrometerMetricsReporter implements Runnable {

    private final WeakConcurrentSet<MeterRegistry> meterRegistries = WeakMapSupplier.createSet();
    private final StringBuilder replaceBuilder = new StringBuilder();
    private final JsonWriter jsonWriter = new DslJson<>().newWriter();
    private final Reporter reporter;
    private final ElasticApmTracer tracer;
    private final AtomicBoolean scheduledReporting = new AtomicBoolean(false);

    public MicrometerMetricsReporter(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.reporter = tracer.getReporter();
    }

    public void registerMeterRegistry(MeterRegistry meterRegistry) {
        boolean added = meterRegistries.add(meterRegistry);
        if (added && scheduledReporting.compareAndSet(false, true)) {
            scheduleReporting();
        }
    }

    private void scheduleReporting() {
        long metricsIntervalMs = tracer.getConfig(ReporterConfiguration.class).getMetricsIntervalMs();
        if (metricsIntervalMs > 0) {
            // called for every class loader that loaded micrometer
            tracer.getSharedSingleThreadedPool().scheduleAtFixedRate(this, metricsIntervalMs, metricsIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    void clear() {
        meterRegistries.clear();
    }

    // guaranteed to be invoked by a single thread
    @Override
    public void run() {
        if (tracer.getState() != Tracer.TracerState.RUNNING) {
            return;
        }
        final long timestamp = System.currentTimeMillis() * 1000;
        for (MeterRegistry meterRegistry : meterRegistries) {
            MicrometerMeterRegistrySerializer.serialize(meterRegistry, timestamp, replaceBuilder, jsonWriter);
        }
        if (jsonWriter.size() > 0) {
            reporter.report(jsonWriter.toByteArray());
            jsonWriter.reset();
        }
    }
}
