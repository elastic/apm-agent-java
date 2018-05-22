/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.opentracing;

import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;

import javax.annotation.Nullable;

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
    public ApmSpan activeSpan() {
        final ApmScope active = scopeManager().active();
        if (active != null) {
            return active.span();
        }
        return null;
    }

    @Override
    public ApmSpanBuilder buildSpan(String operationName) {
        return new ApmSpanBuilder(operationName, scopeManager());
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        // distributed tracing is not supported yet
    }

    @Override
    @Nullable
    public <C> SpanContext extract(Format<C> format, C carrier) {
        // distributed tracing is not supported yet
        return null;
    }
}
