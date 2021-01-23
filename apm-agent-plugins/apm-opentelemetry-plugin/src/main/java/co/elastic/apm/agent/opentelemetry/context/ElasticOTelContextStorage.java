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
package co.elastic.apm.agent.opentelemetry.context;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.opentelemetry.sdk.ElasticOTelSpan;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;

import javax.annotation.Nullable;

public class ElasticOTelContextStorage implements ContextStorage {
    private final ElasticApmTracer elasticApmTracer;

    public ElasticOTelContextStorage(ElasticApmTracer elasticApmTracer) {
        this.elasticApmTracer = elasticApmTracer;
    }

    @Override
    public Scope attach(Context toAttach) {
        Span span = Span.fromContext(toAttach);
        if (span instanceof ElasticOTelSpan) {
            AbstractSpan<?> internalSpan = ((ElasticOTelSpan) span).getInternalSpan();
            elasticApmTracer.activate(internalSpan);
            return new ElasticOTelScope(internalSpan);
        } else {
            return Scope.noop();
        }
    }

    /**
     * NOTE: the returned context is not the same as the one provided in {@link #attach(Context)}.
     * The consequence of this is that it will not have the context keys of the original context.
     * In other words, {@link Context#get(ContextKey)} will return {@code null} for any key besides the span key.
     */
    @Nullable
    @Override
    public Context current() {
        AbstractSpan<?> active = elasticApmTracer.getActive();
        if (active == null) {
            return null;
        }
        return Context.root().with(new ElasticOTelSpan(active));
    }
}
