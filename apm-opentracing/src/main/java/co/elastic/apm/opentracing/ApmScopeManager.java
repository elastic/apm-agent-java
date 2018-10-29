/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

import io.opentracing.ScopeManager;
import io.opentracing.Span;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class ApmScopeManager implements ScopeManager {

    @Override
    public ApmScope activate(@Nonnull Span span, boolean finishSpanOnClose) {
        // prevents other threads from setting ApmSpan.dispatcher to null
        final ApmSpan apmSpan = (ApmSpan) span;
        synchronized (span) {
            doActivate(apmSpan.getSpan(), apmSpan.context().getTraceContext());
        }
        return new ApmScope(finishSpanOnClose, apmSpan);
    }

    private void doActivate(@Nullable Object span, Object traceContext) {
        // implementation is injected at runtime via co.elastic.apm.opentracing.impl.ScopeManagerInstrumentation
    }

    @Override
    @Nullable
    public ApmScope active() {
        final Object span = getCurrentSpan();
        if (span != null) {
            return new ApmScope(false, new ApmSpan(span));
        } else {
            final Object traceContext = getCurrentTraceContext();
            if (traceContext != null) {
                return new ApmScope(false, new ApmSpan(new TraceContextSpanContext(traceContext)));
            }
        }
        return null;
    }

    @Nullable
    private Object getCurrentSpan() {
        // implementation is injected at runtime via co.elastic.apm.opentracing.impl.ScopeManagerInstrumentation
        return null;
    }

    @Nullable
    private Object getCurrentTraceContext() {
        // implementation is injected at runtime via co.elastic.apm.opentracing.impl.ScopeManagerInstrumentation
        return null;
    }
}
