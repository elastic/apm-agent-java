/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.opentracing;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

import javax.annotation.Nullable;
import java.util.Map;

public class ElasticApmTracer implements io.opentracing.Tracer {
    private final ApmScopeManager scopeManager;

    public ElasticApmTracer() {
        this.scopeManager = new ApmScopeManager();
    }

    @Override
    public ApmScopeManager scopeManager() {
        return scopeManager;
    }

    @Override
    @Nullable
    public Span activeSpan() {
        return scopeManager.activeSpan();
    }

    @Override
    public Scope activateSpan(Span span) {
        return scopeManager.activate(span);
    }

    @Override
    public SpanBuilder buildSpan(String operationName) {
        return new ApmSpanBuilder(operationName, scopeManager());
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        if (format == Format.Builtin.HTTP_HEADERS || format == Format.Builtin.TEXT_MAP) {
            TextMap textMap = (TextMap) carrier;
            for (Map.Entry<String, String> baggageItem : spanContext.baggageItems()) {
                textMap.put(baggageItem.getKey(), baggageItem.getValue());
            }
        }
    }

    @Override
    @Nullable
    public <C> SpanContext extract(Format<C> format, C carrier) {
        if (format == Format.Builtin.HTTP_HEADERS || format == Format.Builtin.TEXT_MAP) {
            TextMap textMap = (TextMap) carrier;
            return ExternalProcessSpanContext.of(textMap);
        }
        return null;
    }

    @Override
    public void close() {
        ApmScope active = scopeManager().active();
        if (active != null) {
            active.close();
        }
        // co.elastic.apm.agent.opentracing.impl.ElasticApmTracerInstrumentation#close
    }
}
