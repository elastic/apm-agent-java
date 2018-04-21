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

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Transaction;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

import javax.annotation.Nullable;

class ApmScopeManager implements ScopeManager {

    private final ElasticApmTracer tracer;

    ApmScopeManager(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public ApmScope activate(Span span, boolean finishSpanOnClose) {
        final ApmSpan apmSpan = (ApmSpan) span;
        if (apmSpan.isTransaction()) {
            tracer.activate(apmSpan.getTransaction());
        } else {
            tracer.activate(apmSpan.getSpan());
        }
        return new ApmScope(finishSpanOnClose, apmSpan, tracer);
    }

    @Override
    @Nullable
    public ApmScope active() {
        final co.elastic.apm.impl.transaction.Span span = tracer.currentSpan();
        final Transaction transaction = tracer.currentTransaction();
        if (span == null && transaction == null) {
            return null;
        } else if (span != null) {
            return new ApmScope(false, new ApmSpan(null, span, tracer), tracer);
        } else {
            return new ApmScope(false, new ApmSpan(transaction, null, tracer), tracer);
        }
    }
}
