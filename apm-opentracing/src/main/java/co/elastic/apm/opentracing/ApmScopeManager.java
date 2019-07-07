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

import io.opentracing.ScopeManager;
import io.opentracing.Span;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

class ApmScopeManager implements ScopeManager {

    private static final Logger logger = new Logger();

    private final ThreadLocal<Deque<ApmScope>> activeStack = new ThreadLocal<Deque<ApmScope>>() {
        @Override
        protected Deque<ApmScope> initialValue() {
            return new ArrayDeque<ApmScope>();
        }
    };

    @Override
    public ApmScope activate(@Nonnull Span span, boolean finishSpanOnClose) {
        final ApmSpan apmSpan = (ApmSpan) span;
        final Object traceContext = apmSpan.context().getTraceContext();
        if (traceContext != null) {
            // prevents other threads from concurrently setting ApmSpan#dispatcher to null through ApmSpan.finish.
            // we can't synchronize on the internal span object, as it might be finished already
            // we can't synchronize on the ApmSpan, as we better not rely on any specific ApmScope
            synchronized (traceContext) {
                // apmSpan.getSpan() has to be called within the synchronized block to avoid race conditions,
                // so we can't do the synchronization in ScopeManagerInstrumentation
                doActivate(apmSpan.getSpan(), traceContext);
            }
        }
        ApmScope scope = new ApmScope(this, finishSpanOnClose, apmSpan);
        activeStack.get().push(scope);
        logger.debug("Activated OpenTracing scope", null);
        return scope;
    }

    private void doActivate(@Nullable Object span, Object traceContext) {
        // implementation is injected at runtime via co.elastic.apm.agent.opentracing.impl.ScopeManagerInstrumentation
    }

    void deactivate(ApmScope scope) {
        ApmScope activeScope = null;
        Deque<ApmScope> stack = activeStack.get();

        if (!stack.isEmpty()) {
            activeScope = stack.pop();
        }

        if (scope == activeScope) {
            logger.debug("Deactivated OpenTracing scope", null);
        } else {
            // someone is attempting to close a span it did not create - clearing the stack to avoid leak
            stack.clear();
            logger.warn("An OpenTracing user is trying to close a scope which it apparently did not create: " +
                scope.span().context().getTraceContext(), new Throwable());
        }
    }

    @Override
    @Nullable
    public ApmScope active() {
        ApmScope scope = activeStack.get().peek();
        final Object span = getCurrentSpan();
        if (span != null) {
            if (scope != null && scope.span().isSameSpan(span)) {
                return scope;
            }
            return new ApmScope(this, false, new ApmSpan(span));
        } else {
            final Object traceContext = getCurrentTraceContext();
            if (traceContext != null) {
                if (scope != null && scope.span().isSameContext(traceContext)) {
                    return scope;
                }
                return new ApmScope(this, false, new ApmSpan(new TraceContextSpanContext(traceContext)));
            }
        }
        return null;
    }

    @Nullable
    private Object getCurrentSpan() {
        // implementation is injected at runtime via co.elastic.apm.agent.opentracing.impl.ScopeManagerInstrumentation
        return null;
    }

    @Nullable
    private Object getCurrentTraceContext() {
        // implementation is injected at runtime via co.elastic.apm.agent.opentracing.impl.ScopeManagerInstrumentation
        return null;
    }
}
