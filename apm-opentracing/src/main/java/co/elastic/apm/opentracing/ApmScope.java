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

class ApmScope implements Scope {

    private final ApmScopeManager apmScopeManager;
    private final boolean finishSpanOnClose;
    private final ApmSpan apmSpan;

    ApmScope(ApmScopeManager apmScopeManager, boolean finishSpanOnClose, ApmSpan apmSpan) {
        this.apmScopeManager = apmScopeManager;
        this.finishSpanOnClose = finishSpanOnClose;
        this.apmSpan = apmSpan;
    }

    @Override
    public void close() {
        apmScopeManager.deactivate(this);
        release(apmSpan.getSpan(), apmSpan.context().getTraceContext());
        if (finishSpanOnClose) {
            apmSpan.finish();
        }
    }

    private void release(Object span, Object traceContext) {
        // implementation is injected at runtime via co.elastic.apm.agent.opentracing.impl.ApmScopeInstrumentation
    }

    @Override
    public ApmSpan span() {
        return apmSpan;
    }

    @Override
    public String toString() {
        return String.format("Scope(%s)", apmSpan);
    }
}
