/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
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
package co.elastic.apm.agent.impl;


import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.objectpool.Recyclable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

@VisibleForAdvice
public class InScopeRunnableWrapper implements Runnable, Recyclable {
    private final Logger logger = LoggerFactory.getLogger(InScopeRunnableWrapper.class);

    @Nullable
    private Runnable delegate;
    @Nullable
    private AbstractSpan<?> span;

    private final ElasticApmTracer tracer;

    InScopeRunnableWrapper(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    public InScopeRunnableWrapper wrap(Runnable delegate, AbstractSpan<?> span) {
        this.delegate = delegate;
        this.span = span;
        return this;
    }

    @Override
    public void run() {
        if (span != null) {
            try {
                span.activate();
            } catch (Throwable throwable) {
                try {
                    logger.warn("Failed to activate span");
                } catch (Throwable t) {
                    // do nothing, just never fail
                }
            }
        }

        try {
            //noinspection ConstantConditions
            delegate.run();
        } finally {
            try {
                if (span != null) {
                    span.deactivate();
                }
                tracer.recycle(this);
            } catch (Throwable throwable) {
                try {
                    logger.warn("Failed to deactivate span or recycle");
                } catch (Throwable t) {
                    // do nothing, just never fail
                }
            }
        }
    }

    @Override
    public void resetState() {
        delegate = null;
        span = null;
    }
}
