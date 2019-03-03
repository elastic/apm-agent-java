/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.impl.async;


import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.objectpool.Recyclable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

@VisibleForAdvice
public class ContextInScopeRunnableWrapper implements Runnable, Recyclable {
    private static final Logger logger = LoggerFactory.getLogger(ContextInScopeRunnableWrapper.class);
    private final ElasticApmTracer tracer;
    private final TraceContext context;
    @Nullable
    private volatile Runnable delegate;

    public ContextInScopeRunnableWrapper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        context = TraceContext.with64BitId(tracer);
    }

    public ContextInScopeRunnableWrapper wrap(Runnable delegate, TraceContext context) {
        this.context.copyFrom(context);
        // ordering is important: volatile write has to be after copying the TraceContext to ensure visibility in #run
        this.delegate = delegate;
        return this;
    }

    // Exceptions in the agent may never affect the monitored application
    // normally, advices act as the boundary of user and agent code and exceptions are handled via @Advice.OnMethodEnter(suppress = Throwable.class)
    // In this case, this class acts as the boundary of user and agent code so we have to do the tedious exception handling here
    @Override
    public void run() {
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
            delegate.run();
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
