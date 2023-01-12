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
import co.elastic.apm.agent.opentelemetry.tracing.OTelTracer;
import co.elastic.apm.agent.opentelemetry.tracing.OTelTracerProvider;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;

public class ElasticOpenTelemetry implements OpenTelemetry {

    private final ContextPropagators contextPropagators;
    private final TracerProvider tracerProvider;

    public ElasticOpenTelemetry(ElasticApmTracer tracer) {
        tracerProvider = new OTelTracerProvider(new OTelTracer(tracer));
        contextPropagators = ContextPropagators.create(W3CTraceContextPropagator.getInstance());
    }

    @Override
    public TracerProvider getTracerProvider() {
        return tracerProvider;
    }

    @Override
    public ContextPropagators getPropagators() {
        return contextPropagators;
    }
}
