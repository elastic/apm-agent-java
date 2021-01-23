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

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class ElasticOTelSpan implements Span {
    private final AbstractSpan<?> span;

    public ElasticOTelSpan(AbstractSpan<?> span) {
        this.span = span;
    }

    @Override
    public <T> Span setAttribute(AttributeKey<T> key, @Nonnull T value) {
        AttributeMapper.mapAttribute(span, key, value);
        return this;
    }

    @Override
    public Span addEvent(String name, Attributes attributes) {
        return this;
    }

    @Override
    public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
        return this;
    }

    @Override
    public Span setStatus(StatusCode statusCode, String description) {
        // TODO set outcome
        return this;
    }

    @Override
    public Span recordException(Throwable exception, Attributes additionalAttributes) {
        span.captureException(exception);
        return this;
    }

    @Override
    public Span updateName(String name) {
        span.withName(name);
        return this;
    }

    @Override
    public void end() {
        span.end();
    }

    @Override
    public void end(long timestamp, TimeUnit unit) {
        span.end(unit.toMicros(timestamp));
    }

    @Override
    public SpanContext getSpanContext() {
        return new ElasticOTelSpanContext(span.getTraceContext());
    }

    @Override
    public boolean isRecording() {
        return span.isSampled();
    }

    public AbstractSpan<?> getInternalSpan() {
        return span;
    }
}
