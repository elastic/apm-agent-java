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

import io.opentracing.ScopeManager;
import io.opentracing.Span;

import javax.annotation.Nullable;

class ApmScopeManager implements ScopeManager {

    @Override
    public ApmScope activate(Span span, boolean finishSpanOnClose) {
        final ApmSpan apmSpan = (ApmSpan) span;
        doActivate(apmSpan.getTransaction(), apmSpan.getSpan());
        return new ApmScope(finishSpanOnClose, apmSpan);
    }

    private void doActivate(@Nullable Object transaction, @Nullable Object span) {
        // implementation is injected at runtime via co.elastic.apm.opentracing.impl.ScopeManagerInstrumentation
    }

    @Override
    @Nullable
    public ApmScope active() {
        final Object span = getCurrentSpan();
        final Object transaction = getCurrentTransaction();
        if (span == null && transaction == null) {
            return null;
        } else if (span != null) {
            return new ApmScope(false, new ApmSpan(null, span));
        } else {
            return new ApmScope(false, new ApmSpan(transaction, null));
        }
    }

    @Nullable
    private Object getCurrentTransaction() {
        // implementation is injected at runtime via co.elastic.apm.opentracing.impl.ScopeManagerInstrumentation
        return null;
    }

    @Nullable
    private Object getCurrentSpan() {
        // implementation is injected at runtime via co.elastic.apm.opentracing.impl.ScopeManagerInstrumentation
        return null;
    }
}
