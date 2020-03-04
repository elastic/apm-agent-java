/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.impl.async;


import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.objectpool.Recyclable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

@VisibleForAdvice
public class ContextInScopeCallableWrapper<V> implements Callable<V>, Recyclable {
    private static final Logger logger = LoggerFactory.getLogger(ContextInScopeCallableWrapper.class);
    private final ElasticApmTracer tracer;
    private final TraceContext context;
    @Nullable
    private volatile Callable<V> delegate;

    public ContextInScopeCallableWrapper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        context = TraceContext.with64BitId(tracer);
    }

    public ContextInScopeCallableWrapper<V> wrap(Callable<V> delegate, TraceContext context) {
        this.context.copyFrom(context);
        // ordering is important: volatile write has to be after copying the TraceContext to ensure visibility in #run
        this.delegate = delegate;
        return this;
    }

    // Exceptions in the agent may never affect the monitored application
    // normally, advices act as the boundary of user and agent code and exceptions are handled via @Advice.OnMethodEnter(suppress = Throwable.class)
    // In this case, this class acts as the boundary of user and agent code so we have to do the tedious exception handling here
    @Override
    public V call() throws Exception {
        try {
            context.activate();
        } catch (Throwable t) {
            try {
                logger.error("Unexpected error while activating span", t);
            } catch (Throwable ignore) {
            }
        }
        try {
            //noinspection ConstantConditions
            return delegate.call();
        } finally {
            try {
                context.deactivate();
                tracer.recycle(this);
            } catch (Throwable t) {
                try {
                    logger.error("Unexpected error while deactivating or recycling span", t);
                } catch (Throwable ignore) {
                }
            }
        }
    }

    @Override
    public void resetState() {
        context.resetState();
        delegate = null;
    }
}
