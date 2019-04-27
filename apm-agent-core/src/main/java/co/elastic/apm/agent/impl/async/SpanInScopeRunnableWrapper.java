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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;

@VisibleForAdvice
public class SpanInScopeRunnableWrapper extends SpanInScopeBaseWrapper implements Runnable, Recyclable {

    @Nullable
    private volatile Runnable delegate;
    @Nullable
    private volatile AbstractSpan<?> span;

    public SpanInScopeRunnableWrapper(ElasticApmTracer tracer) {
        super(tracer);
    }

    public SpanInScopeRunnableWrapper wrap(Runnable delegate, AbstractSpan<?> span) {
        this.delegate = delegate;
        this.span = span;
        return this;
    }

    // Exceptions in the agent may never affect the monitored application
    // normally, advices act as the boundary of user and agent code and exceptions are handled via @Advice.OnMethodEnter(suppress = Throwable.class)
    // In this case, this class acts as the boundary of user and agent code so we have to do the tedious exception handling here
    @Override
    public void run() {
        // minimize volatile reads
        AbstractSpan<?> localSpan = span;
        boolean activated = beforeDelegation(localSpan);
        try {
            //noinspection ConstantConditions
            delegate.run();
            // the span may be ended at this point
        } finally {
            afterDelegation(localSpan, activated);
        }
    }

    @Override
    public void resetState() {
        delegate = null;
        span = null;
    }

    @Override
    protected void doRecycle() {
        tracer.recycle(this);
    }
}
