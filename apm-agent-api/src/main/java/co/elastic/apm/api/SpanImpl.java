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
package co.elastic.apm.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * If the agent is active, it injects the implementation into this class.
 * <p>
 * Otherwise, this class is a noop.
 * </p>
 */
class SpanImpl extends AbstractSpanImpl {

    SpanImpl(@Nonnull Object span) {
        super(span);
    }

    @Nonnull
    @Override
    public Span setName(String name) {
        doSetName(name);
        return this;
    }

    @Nonnull
    @Override
    public Span setType(String type) {
        doSetType(type);
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Span addTag(String key, String value) {
        doAddTag(key, value);
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Span addLabel(String key, String value) {
        doAddStringLabel(key, value);
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Span addLabel(String key, Number value) {
        doAddNumberLabel(key, value);
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Span addLabel(String key, boolean value) {
        doAddBooleanLabel(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Span setLabel(String key, String value) {
        doAddStringLabel(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Span setLabel(String key, Number value) {
        doAddNumberLabel(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Span setLabel(String key, boolean value) {
        doAddBooleanLabel(key, value);
        return this;
    }

    @Override
    public Span setStartTimestamp(long epochMicros) {
        doSetStartTimestamp(epochMicros);
        return this;
    }

    @Override
    public Span setOutcome(Outcome outcome) {
        doSetOutcome(outcome);
        return this;
    }

    @Nonnull
    @Override
    public Span setDestinationAddress(@Nullable String address, int port) {
        doSetDestinationAddress(address, port);
        return this;
    }

    @Nonnull
    @Override
    public Span setDestinationService(@Nullable String resource) {
        doSetDestinationService(resource);
        return this;
    }
}
