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
import co.elastic.apm.agent.impl.baggage.BaggageImpl;
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.MultiValueMapAccessor;
import co.elastic.apm.agent.impl.transaction.OTelSpanKind;
import co.elastic.apm.agent.impl.transaction.TraceContextImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.opentelemetry.baggage.OtelBaggage;
import co.elastic.apm.agent.sdk.internal.util.LoggerUtils;
import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;
import co.elastic.apm.agent.sdk.internal.util.VersionUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.metadata.PotentiallyMultiValuedMap;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class OTelSpanBuilder implements SpanBuilder {

    private static final Logger addLinkLogger1 = LoggerUtils.logOnce(LoggerFactory.getLogger(OTelSpanBuilder.class));
    private static final Logger addLinkLogger2 = LoggerUtils.logOnce(LoggerFactory.getLogger(OTelSpanBuilder.class));

    private final String spanName;
    private final ElasticApmTracer elasticApmTracer;
    private final Map<AttributeKey<?>, Object> attributes = new HashMap<>();
    private long epochMicros = -1;
    @Nullable
    private Context parent;

    private List<SpanContext> links = new ArrayList<>();

    @Nullable
    private SpanKind kind;

    public OTelSpanBuilder(String spanName, ElasticApmTracer elasticApmTracer) {
        this.spanName = spanName;
        this.elasticApmTracer = elasticApmTracer;
    }

    @Override
    public SpanBuilder setParent(Context context) {
        parent = context;
        return this;
    }

    @Override
    public SpanBuilder setNoParent() {
        parent = null;
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext) {
        if (!(spanContext instanceof OTelSpanContext)) {
            addLinkLogger2.warn("Adding arbitrary span context to links is currently unsupported");
            return this;
        }
        links.add(spanContext);
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext, @Nullable Attributes attributes1) {
        addLink(spanContext);
        if (attributes1 != null && !attributes1.isEmpty()) {
            addLinkLogger1.warn("Adding attributes to links is currently unsupported - the links have been added but with no attributes, the following attributes have been ignored: %s", attributes1);
        }
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, @Nonnull String value) {
        setAttribute(AttributeKey.stringKey(key), value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, long value) {
        setAttribute(AttributeKey.longKey(key), value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, double value) {
        setAttribute(AttributeKey.doubleKey(key), value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, boolean value) {
        setAttribute(AttributeKey.booleanKey(key), value);
        return this;
    }

    @Override
    public <T> SpanBuilder setAttribute(AttributeKey<T> key, @Nonnull T value) {
        attributes.put(key, value);
        return this;
    }

    @Override
    public SpanBuilder setSpanKind(SpanKind spanKind) {
        kind = spanKind;
        return this;
    }

    @Override
    public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
        this.epochMicros = unit.toMicros(startTimestamp);
        return this;
    }

    @Override
    public Span startSpan() {
        AbstractSpanImpl<?> span;

        BaggageImpl parentBaggage;
        AbstractSpanImpl<?> parentSpan = null;
        Context remoteContext = null;

        if (parent != null) {
            Span parentOtelSpan = Span.fromContext(parent);
            if (parentOtelSpan.getSpanContext().isRemote()) {
                remoteContext = parent;
            } else if (parentOtelSpan instanceof OTelSpan) {
                parentSpan = ((OTelSpan) parentOtelSpan).getInternalSpan();
            }
            parentBaggage = OtelBaggage.toElasticBaggage(io.opentelemetry.api.baggage.Baggage.fromContext(parent));
        } else {
            // when parent is not explicitly set, the currently active parent is used as fallback
            parentSpan = elasticApmTracer.currentContext().getSpan();
            parentBaggage = elasticApmTracer.currentContext().getBaggage();
        }

        if (remoteContext != null) {
            PotentiallyMultiValuedMap headers = new PotentiallyMultiValuedMap(2);
            W3CTraceContextPropagator.getInstance().inject(remoteContext, headers, PotentiallyMultiValuedMap::add);
            span = elasticApmTracer.startChildTransaction(headers, MultiValueMapAccessor.INSTANCE, PrivilegedActionUtils.getClassLoader(getClass()), parentBaggage, epochMicros);
        } else if (parentSpan == null) {
            span = elasticApmTracer.startRootTransaction(PrivilegedActionUtils.getClassLoader(getClass()), parentBaggage, epochMicros);
        } else {
            span = elasticApmTracer.startSpan(parentSpan, parentBaggage, epochMicros);
        }
        if (span == null) {
            return Span.getInvalid();
        }
        span.withName(spanName);

        if (span instanceof TransactionImpl) {
            TransactionImpl t = ((TransactionImpl) span);
            t.setFrameworkName("OpenTelemetry API");

            String otelVersion = VersionUtils.getVersion(OpenTelemetry.class, "io.opentelemetry", "opentelemetry-api");
            if (otelVersion != null) {
                t.setFrameworkVersion(otelVersion);
            }
        }

        OTelSpanKind otelKind = OTelHelper.map(kind);
        span.withOtelKind(otelKind);

        // When otel span kind have been explicitly set to "client", this is equivalent to an "exit" span
        // Thus no further child spans are expected.
        if(otelKind == OTelSpanKind.CLIENT) {
             span.asExit();
        }

        // With OTel API, the status (bridged to outcome) should only be explicitly set, thus we have to set and use
        // user outcome to provide higher priority and avoid inferring outcome from any reported exception
        span.withUserOutcome(Outcome.UNKNOWN);

        // Add the links to the span
        for (int i = 0; i < links.size(); i++) {
            span.addSpanLink(TraceContextImpl.fromParentContext(), ((OTelSpanContext) links.get(i)).getElasticTraceContext());

        }

        OTelSpan otelSpan = new OTelSpan(span);
        attributes.forEach((AttributeKey<?> k, Object v) -> otelSpan.setAttribute((AttributeKey<? super Object>) k, (Object) v));
        return otelSpan;
    }

}
