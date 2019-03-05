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

import co.elastic.apm.agent.impl.ActivationListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.objectpool.Recyclable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * An abstraction of both {@link TraceContext} and {@link AbstractSpan}.
 * Given an instance of this class,
 * you can create child spans,
 * capture exceptions and manage activations/scopes.
 * <p>
 * This abstraction reliefs clients from having to differ between the case
 * when the current activation is a {@link TraceContext} vs an {@link AbstractSpan}.
 * </p>
 * <p>
 * A {@link TraceContext} would be active when the current thread does not own the lifecycle of the parent span.
 * Otherwise an {@link AbstractSpan} would be active.
 * </p>
 *
 * @param <T> the type, used to enable fluent method chaining
 */
public abstract class TraceContextHolder<T extends TraceContextHolder> implements Recyclable {

    private static final Logger logger = LoggerFactory.getLogger(TraceContextHolder.class);

    protected final ElasticApmTracer tracer;

    protected TraceContextHolder(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    public abstract TraceContext getTraceContext();

    public abstract Span createSpan();

    public abstract boolean isChildOf(TraceContextHolder other);

    public T activate() {
        List<ActivationListener> activationListeners = tracer.getActivationListeners();
        for (int i = 0; i < activationListeners.size(); i++) {
            try {
                activationListeners.get(i).beforeActivate(this);
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                logger.warn("Exception while calling {}#beforeActivate", activationListeners.get(i).getClass().getSimpleName(), t);
            }
        }
        tracer.activate(this);
        return (T) this;
    }

    public T deactivate() {
        tracer.deactivate(this);
        List<ActivationListener> activationListeners = tracer.getActivationListeners();
        for (int i = 0; i < activationListeners.size(); i++) {
            try {
                activationListeners.get(i).afterDeactivate();
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                logger.warn("Exception while calling {}#afterDeactivate", activationListeners.get(i).getClass().getSimpleName(), t);
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

    public boolean isSampled() {
        return getTraceContext().isSampled();
    }

    public void captureException(long epochMicros, Throwable t) {
        tracer.captureException(epochMicros, t, this);
    }

    public T captureException(@Nullable Throwable t) {
        if (t != null) {
            captureException(getTraceContext().getClock().getEpochMicros(), t);
        }
        return (T) this;
    }

    /**
     * Wraps the provided {@link Runnable} and makes this {@link TraceContext} active in the {@link Runnable#run()} method.
     *
     * <p>
     * Note: does not activate the {@link AbstractSpan} but only the {@link TraceContext}.
     * This is useful if this span is closed in a different thread than the provided {@link Runnable} is executed in.
     * </p>
     */
    public Runnable withActiveContext(Runnable runnable) {
        return tracer.wrapRunnable(runnable, getTraceContext());
    }

    /**
     * Wraps the provided {@link Callable} and makes this {@link TraceContext} active in the {@link Callable#call()} method.
     *
     * <p>
     * Note: does not activate the {@link AbstractSpan} but only the {@link TraceContext}.
     * This is useful if this span is closed in a different thread than the provided {@link java.util.concurrent.Callable} is executed in.
     * </p>
     */
    public <V> Callable<V> withActiveContext(Callable<V> runnable) {
        return tracer.wrapCallable(runnable, getTraceContext());
    }

}
