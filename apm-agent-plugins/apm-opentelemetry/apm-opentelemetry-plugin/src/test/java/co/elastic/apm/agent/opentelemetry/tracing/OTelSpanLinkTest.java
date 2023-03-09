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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.opentelemetry.global.ElasticOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OTelSpanLinkTest {

    @Test
    void checkOneSpanLink() {
        checkSpanLinks(1, false);
    }

    @Test
    void checkTwoSpanLinks() {
        checkSpanLinks(2, false);
    }

    @Test
    void checkOneSpanLinkWithAttributes() {
        checkSpanLinks(1, true);
    }

    void checkSpanLinks(int linkCount, boolean withAttributes) {
        ElasticApmTracer etracer = new ElasticApmTracerBuilder().build();
        assertThat(etracer).isNotNull();

        etracer.start(false);
        Tracer tracer = new ElasticOpenTelemetry(etracer).getTracerProvider().get("checkSpanLinks");
        assertThat(tracer).isInstanceOf(OTelTracer.class);

        SpanBuilder spanbuilder = tracer.spanBuilder("span");
        assertThat(spanbuilder).isInstanceOf(OTelSpanBuilder.class);

        TraceContext[] contexts = new TraceContext[linkCount];
        AttributesBuilder builder = Attributes.builder();
        builder.put("key1", 33);
        builder.put("key2", true);
        for (int i = 0; i < linkCount; i++) {
            TraceContext traceContext1 = TraceContext.with64BitId(etracer);
            traceContext1.asRootSpan(ConstantSampler.of(false));
            SpanContext context1 = new OTelSpanContext(traceContext1);
            if (withAttributes) {
                //The actual attribute addition to the links is not currently supported,
                //but we still want to test that the method correctly adds links
                spanbuilder.addLink(context1, builder.build());
            } else {
                spanbuilder.addLink(context1);
            }
            contexts[i] = traceContext1;
        }

        OTelSpan span = (OTelSpan) spanbuilder.startSpan();
        List<TraceContext> links = span.getInternalSpan().getSpanLinks();

        assertThat(links.size()).isEqualTo(linkCount);
        for (int i = 0; i < linkCount; i++) {
            assertThat(links.get(i).getTraceId()).isEqualTo(contexts[i].getTraceId());
            assertThat(links.get(i).getParentId()).isEqualTo(contexts[i].getId());
        }
    }
}
