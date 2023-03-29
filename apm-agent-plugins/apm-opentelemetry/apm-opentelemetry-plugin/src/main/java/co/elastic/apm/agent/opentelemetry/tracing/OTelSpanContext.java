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
package co.elastic.apm.agent.opentelemetry.tracing;

import co.elastic.apm.agent.impl.transaction.TraceContext;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.TraceStateBuilder;
import org.stagemonitor.util.StringUtils;

import java.util.List;

public class OTelSpanContext implements SpanContext {
    private final TraceContext traceContext;

    protected TraceContext getElasticTraceContext() {return traceContext;}

    public OTelSpanContext(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    @Override
    public String getTraceId() {
        return traceContext.getTraceId().toString();
    }

    @Override
    public String getSpanId() {
        return traceContext.getId().toString();
    }

    @Override
    public TraceFlags getTraceFlags() {
        return TraceFlags.fromByte(traceContext.getFlags());
    }

    @Override
    public TraceState getTraceState() {
        // Lazily parses tracestate header.
        // Our internal TraceState class doesn't parse the raw tracestate header
        // as we currently don't have a use case where the agent needs to read the tracestate.
        TraceStateBuilder builder = TraceState.builder();
        List<String> tracestate = traceContext.getTraceState().getTracestate();
        for (int i = 0, size = tracestate.size(); i < size; i++) {
            for (String vendorEntry : StringUtils.split(tracestate.get(i), ',')) {
                String[] keyValue = StringUtils.split(vendorEntry, '=');
                if (keyValue.length == 2) {
                    builder.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return builder.build();
    }

    @Override
    public boolean isRemote() {
        // the elastic agent doesn't create a TraceContext for remote parents
        // instead, it directly creates an entry child span given the request headers
        return false;
    }

}
