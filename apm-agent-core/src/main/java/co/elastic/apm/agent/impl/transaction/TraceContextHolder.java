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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.SpanListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;

public abstract class TraceContextHolder<T extends TraceContextHolder> {

    private static final Logger logger = LoggerFactory.getLogger(TraceContextHolder.class);

    private final ElasticApmTracer tracer;

    protected TraceContextHolder(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    public abstract TraceContext getTraceContext();

    public abstract Span createSpan();

    public abstract boolean isChildOf(TraceContextHolder other);

    public T activate() {
        try {
            tracer.activate(this);
            List<SpanListener> spanListeners = tracer.getSpanListeners();
            for (int i = 0; i < spanListeners.size(); i++) {
                try {
                    spanListeners.get(i).onActivate(this);
                } catch (Error e) {
                    throw e;
                } catch (Throwable t) {
                    logger.warn("Exception while calling {}#onActivate", spanListeners.get(i).getClass().getSimpleName(), t);
                }
            }
        } catch (Throwable t) {
            try {
                logger.error("Unexpected error while activating context", t);
            } catch (Throwable ignore) {
            }
        }
        return (T) this;
    }

    public T deactivate() {
        try {
            tracer.deactivate(this);
            List<SpanListener> spanListeners = tracer.getSpanListeners();
            for (int i = 0; i < spanListeners.size(); i++) {
                try {
                    spanListeners.get(i).onDeactivate(this);
                } catch (Error e) {
                    throw e;
                } catch (Throwable t) {
                    logger.warn("Exception while calling {}#onDeactivate", spanListeners.get(i).getClass().getSimpleName(), t);
                }
            }
        } catch (Throwable t) {
            try {
                logger.error("Unexpected error while activating context", t);
            } catch (Throwable ignore) {
            }
        }
        return (T) this;
    }

    public Scope activateInScope() {
        // already in scope
        if (tracer.getActive() == this) {
            return Scope.NoopScope.INSTANCE;
        }
        activate();
        return new Scope() {
            @Override
            public void close() {
                deactivate();
            }
        };
    }

    public abstract boolean isSampled();

    public void captureException(long epochMicros, Throwable t) {
        tracer.captureException(epochMicros, t, this);
    }

    public T captureException(@Nullable Throwable t) {
        if (t != null) {
            captureException(getTraceContext().getClock().getEpochMicros(), t);
        }
        return (T) this;
    }

}
