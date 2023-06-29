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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.ElasticContext;
import co.elastic.apm.agent.tracer.Scope;

import javax.annotation.Nullable;

class EmptyElasticContext extends ElasticContext<EmptyElasticContext> {

    static final ElasticContext<?> INSTANCE = new EmptyElasticContext();

    private EmptyElasticContext() {
    }

    @Nullable
    @Override
    public AbstractSpan<?> getAbstractSpan() {
        return null;
    }

    @Override
    public EmptyElasticContext activate() {
        return this;
    }

    @Override
    public EmptyElasticContext deactivate() {
        return this;
    }

    @Override
    public Scope activateInScope() {
        return NoopScope.INSTANCE;
    }

    @Override
    public void incrementReferences() {

    }

    @Override
    public void decrementReferences() {

    }

}
