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
package co.elastic.apm.agent.tracer;

import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.HeaderSetter;

import javax.annotation.Nullable;

public class NoopTraceState implements TraceState<NoopTraceState> {

    static final NoopTraceState INSTANCE = new NoopTraceState();

    @Override
    public NoopTraceState activate() {
        return this;
    }

    @Override
    public NoopTraceState deactivate() {
        return this;
    }

    @Override
    public Scope activateInScope() {
        return new Scope() {
            @Override
            public void close() {
            }
        };
    }

    @Nullable
    @Override
    public AbstractSpan<?> getSpan() {
        return null;
    }

    @Nullable
    @Override
    public Transaction<?> getTransaction() {
        return null;
    }

    @Override
    public Baggage getBaggage() {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public Span<?> createSpan() {
        return null;
    }

    @Nullable
    @Override
    public Span<?> createExitSpan() {
        return null;
    }

    @Override
    public BaggageContextBuilder withUpdatedBaggage() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public <C> void propagateContext(C carrier, HeaderSetter<?, C> headerSetter, HeaderGetter<?, C> headerGetter) {

    }

    @Override
    public <C1, C2> void propagateContext(C1 carrier, HeaderSetter<?, C1> headerSetter, @Nullable C2 carrier2, @Nullable HeaderGetter<?, C2> headerGetter) {

    }

    @Override
    public <C> boolean isPropagationRequired(C carrier, HeaderGetter<?, C> headerGetter) {
        return false;
    }

    @Override
    public boolean shouldSkipChildSpanCreation() {
        return true;
    }

    @Override
    public void incrementReferences() {

    }

    @Override
    public void decrementReferences() {

    }
}
