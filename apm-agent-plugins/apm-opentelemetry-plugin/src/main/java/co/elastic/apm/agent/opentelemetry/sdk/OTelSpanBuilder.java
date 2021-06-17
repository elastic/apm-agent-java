/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.MultiValueMapAccessor;
import co.elastic.apm.agent.util.LoggerUtils;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class OTelSpanBuilder implements SpanBuilder {

    private static final Logger addLinkLogger = LoggerUtils.logOnce(LoggerFactory.getLogger(OTelSpanBuilder.class));

    private final String spanName;
    private final ElasticApmTracer elasticApmTracer;
    private final Map<AttributeKey<?>, Object> attributes = new HashMap<>();
    private long epochMicros = -1;
    @Nullable
    private AbstractSpan<?> parent;
    @Nullable
    private Context remoteContext;

    public OTelSpanBuilder(String spanName, ElasticApmTracer elasticApmTracer) {
        this.spanName = spanName;
        this.elasticApmTracer = elasticApmTracer;
        this.parent = elasticApmTracer.getActive();
    }

    @Override
    public SpanBuilder setParent(Context context) {
        Span span = Span.fromContext(context);
        if (span.getSpanContext().isRemote()) {
            remoteContext = context;
        } else if (span instanceof OTelSpan) {
            parent = ((OTelSpan) span).getInternalSpan();
        }
        return this;
    }

    @Override
    public SpanBuilder setNoParent() {
        parent = null;
        remoteContext = null;
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext) {
        addLinkLogger.warn("The addLink API is not supported at the moment");
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext, Attributes attributes) {
        addLinkLogger.warn("The addLink API is not supported at the moment");
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
        return this;
    }

    @Override
    public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
        this.epochMicros = unit.toMicros(startTimestamp);
        return this;
    }

    @Override
    public Span startSpan() {
        AbstractSpan<?> span;
        if (remoteContext != null) {
            PotentiallyMultiValuedMap headers = new PotentiallyMultiValuedMap(2);
            W3CTraceContextPropagator.getInstance().inject(remoteContext, headers, PotentiallyMultiValuedMap::add);
            span = elasticApmTracer.startChildTransaction(headers, MultiValueMapAccessor.INSTANCE, getClass().getClassLoader(), epochMicros);
        } else if (parent == null) {
            span = elasticApmTracer.startRootTransaction(getClass().getClassLoader(), epochMicros);
        } else {
            span = elasticApmTracer.startSpan(parent, epochMicros);
        }
        if (span == null) {
            return Span.getInvalid();
        }
        span.withName(spanName);
        OTelSpan otelSpan = new OTelSpan(span);
        attributes.forEach(otelSpan::mapAttribute);
        return otelSpan;
    }
}
