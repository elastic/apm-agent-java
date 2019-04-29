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
import co.elastic.apm.agent.impl.context.AbstractContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractSpan<T extends AbstractSpan> extends TraceContextHolder<T> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractSpan.class);
    protected static final double MS_IN_MICROS = TimeUnit.MILLISECONDS.toMicros(1);
    protected final TraceContext traceContext;

    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    protected final StringBuilder name = new StringBuilder();
    private long timestamp;

    /**
     * How long the transaction took to complete, in ms with 3 decimal points
     * (Required)
     */
    protected double duration;
    protected AtomicInteger references = new AtomicInteger();
    protected volatile boolean finished = true;

    public int getReferenceCount() {
        return references.get();
    }

    public AbstractSpan(ElasticApmTracer tracer) {
        super(tracer);
        traceContext = TraceContext.with64BitId(this.tracer);
    }

    public boolean isReferenced() {
        return references.get() > 0;
    }

    /**
     * How long the transaction took to complete, in ms with 3 decimal points
     * (Required)
     */
    public double getDuration() {
        return duration;
    }

    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    public StringBuilder getName() {
        return name;
    }

    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    public void setName(@Nullable String name) {
        this.name.setLength(0);
        this.name.append(name);
    }

    /**
     * Appends a string to the name.
     * <p>
     * This method helps to avoid the memory allocations of string concatenations
     * as the underlying {@link StringBuilder} instance will be reused.
     * </p>
     *
     * @param s the string to append to the name
     * @return {@code this}, for chaining
     */
    public T appendToName(String s) {
        name.append(s);
        return (T) this;
    }

    /**
     * Recorded time of the span or transaction in microseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public TraceContext getTraceContext() {
        return traceContext;
    }

    @Override
    public void resetState() {
        super.resetState();
        finished = true;
        name.setLength(0);
        timestamp = 0;
        duration = 0;
        traceContext.resetState();
        references.set(0);
    }

    public boolean isChildOf(AbstractSpan<?> parent) {
        return traceContext.isChildOf(parent.traceContext);
    }

    @Override
    public Span createSpan() {
        return createSpan(traceContext.getClock().getEpochMicros());
    }

    @Override
    public Span createSpan(long epochMicros) {
        return tracer.startSpan(this, epochMicros);
    }

    public void addLabel(String key, String value) {
        if (isSampled()) {
            getContext().addLabel(key, value);
        }
    }

    public void addLabel(String key, Number value) {
        if (isSampled()) {
            getContext().addLabel(key, value);
        }
    }

    public void addLabel(String key, Boolean value) {
        if (isSampled()) {
            getContext().addLabel(key, value);
        }
    }

    public abstract AbstractContext getContext();

    /**
     * Called after the span has been started and its parent references are set
     */
    protected void onAfterStart() {
        this.finished = false;
        // this final reference is decremented when the span is reported
        // or even after its reported and the last child span is ended
        incrementReferences();
    }

    public void end() {
        end(traceContext.getClock().getEpochMicros());
    }

    public final void end(long epochMicros) {
        if (!finished) {
            this.duration = (epochMicros - timestamp)  / AbstractSpan.MS_IN_MICROS;
            if (name.length() == 0) {
                name.append("unnamed");
            }
            doEnd(epochMicros);
            // has to be set last so doEnd callbacks don't think it has already been finished
            this.finished = true;
        } else {
            logger.warn("End has already been called: {}", this);
            assert false;
        }
    }

    protected abstract void doEnd(long epochMicros);

    @Override
    public boolean isChildOf(TraceContextHolder other) {
        return getTraceContext().isChildOf(other);
    }

    @Override
    public T activate() {
        incrementReferences();
        return super.activate();
    }

    @Override
    public T deactivate() {
        try {
            return super.deactivate();
        } finally {
            decrementReferences();
        }
    }

    /**
     * Wraps the provided runnable and makes this {@link AbstractSpan} active in the {@link Runnable#run()} method.
     *
     * <p>
     * Note: does activates the {@link AbstractSpan} and not only the {@link TraceContext}.
     * This should only be used when the span is closed in thread the provided {@link Runnable} is executed in.
     * </p>
     */
    @Override
    public Runnable withActive(Runnable runnable) {
        return tracer.wrapRunnable(runnable, this);
    }

    /**
     * Wraps the provided runnable and makes this {@link AbstractSpan} active in the {@link Runnable#run()} method.
     *
     * <p>
     * Note: does activates the {@link AbstractSpan} and not only the {@link TraceContext}.
     * This should only be used when the span is closed in thread the provided {@link Runnable} is executed in.
     * </p>
     */
    @Override
    public <V> Callable<V> withActive(Callable<V> callable) {
        return tracer.wrapCallable(callable, this);
    }

    public void setStartTimestamp(long epochMicros) {
        timestamp = epochMicros;
    }

    public void incrementReferences() {
        references.incrementAndGet();
        if (logger.isDebugEnabled()) {
            logger.debug("increment references to {} ({})", this, references);
            if (logger.isTraceEnabled()) {
                logger.trace("incrementing references at",
                    new RuntimeException("This is an expected exception. Is just used to record where the reference count has been incremented."));
            }
        }
    }

    public void decrementReferences() {
        if (logger.isDebugEnabled()) {
            logger.debug("decrement references to {} ({})", this, references);
            if (logger.isTraceEnabled()) {
                logger.trace("decrementing references at",
                    new RuntimeException("This is an expected exception. Is just used to record where the reference count has been decremented."));
            }
        }
    }

}
