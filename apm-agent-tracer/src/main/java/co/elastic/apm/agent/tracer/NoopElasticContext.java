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

import co.elastic.apm.agent.tracer.dispatch.BinaryHeaderSetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;

import javax.annotation.Nullable;

public class NoopElasticContext implements ElasticContext<NoopElasticContext> {

    static final NoopElasticContext INSTANCE = new NoopElasticContext();

    @Override
    public NoopElasticContext activate() {
        return this;
    }

    @Override
    public NoopElasticContext deactivate() {
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
    public boolean isEmpty() {
        return false;
    }

    @Override
    public <C> boolean propagateContext(C carrier, BinaryHeaderSetter<C> headerSetter) {
        return false;
    }

    @Override
    public <C> void propagateContext(C carrier, TextHeaderSetter<C> headerSetter, TextHeaderGetter<C> headerGetter) {

    }

    @Override
    public <C1, C2> void propagateContext(C1 carrier, TextHeaderSetter<C1> headerSetter, @Nullable C2 carrier2, @Nullable TextHeaderGetter<C2> headerGetter) {
        
    }

    @Override
    public <C> boolean isPropagationRequired(C carrier, TextHeaderGetter<C> headerGetter) {
        return false;
    }

    @Override
    public void incrementReferences() {

    }

    @Override
    public void decrementReferences() {

    }
}
