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
package co.elastic.apm.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

enum NoopSpan implements Span {
    INSTANCE;

    @Nonnull
    @Override
    public Span setName(String name) {
        // noop
        return this;
    }

    @Nonnull
    @Override
    public Span setType(String type) {
        // noop
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Span addTag(String key, String value) {
        // noop
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Span addLabel(String key, String value) {
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Span addLabel(String key, Number value) {
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Span addLabel(String key, boolean value) {
        return this;
    }

    @Nonnull
    @Override
    public Span setLabel(String key, String value) {
        return this;
    }

    @Nonnull
    @Override
    public Span setLabel(String key, Number value) {
        return this;
    }

    @Nonnull
    @Override
    public Span setLabel(String key, boolean value) {
        return this;
    }

    @Override
    public void end() {
        // noop
    }

    @Override
    public void end(long epochMicros) {
        // noop
    }

    @Override
    public String captureException(Throwable throwable) {
        // co.elastic.apm.agent.plugin.api.CaptureExceptionInstrumentation
        return "";
    }

    @Nonnull
    @Override
    public String getId() {
        return "";
    }

    @Nonnull
    @Override
    public String getTraceId() {
        return "";
    }

    @Override
    public Scope activate() {
        return NoopScope.INSTANCE;
    }

    @Override
    public boolean isSampled() {
        return false;
    }

    @Nonnull
    @Override
    public Span createSpan() {
        return INSTANCE;
    }

    @Nonnull
    @Override
    public Span startSpan(String type, @Nullable String subtype, @Nullable String action) {
        return INSTANCE;
    }

    @Nonnull
    @Override
    public Span startSpan() {
        return INSTANCE;
    }

    @Override
    public Span setStartTimestamp(long epochMicros) {
        return INSTANCE;
    }

    @Override
    public void injectTraceHeaders(HeaderInjector headerInjector) {
        // noop
    }

    @Nonnull
    @Override
    public Span withDestinationServiceResource(@Nullable String resource) {
        return INSTANCE;
    }

    @Nonnull
    @Override
    public Span withDestinationServiceName(@Nullable String name) {
        return INSTANCE;
    }

    @Nonnull
    @Override
    public Span setDestinationServiceType(@Nullable String type) {
        return INSTANCE;
    }
}
