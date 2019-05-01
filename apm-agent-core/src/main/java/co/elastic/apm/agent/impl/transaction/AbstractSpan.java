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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.AbstractContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public abstract class AbstractSpan<T extends AbstractSpan> extends TraceContextHolder<T> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractSpan.class);
    protected static final double MS_IN_MICROS = TimeUnit.MILLISECONDS.toMicros(1);
    protected final TraceContext traceContext;

    // used to mark this span as expected to switch lifecycle-managing-thread, eg span created by one thread and ended by another
    private volatile boolean isLifecycleManagingThreadSwitch;

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

    private volatile boolean finished = true;

    public AbstractSpan(ElasticApmTracer tracer) {
        super(tracer);
        traceContext = TraceContext.with64BitId(this.tracer);
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
        isLifecycleManagingThreadSwitch = false;
        traceContext.resetState();
    }

    public boolean isChildOf(AbstractSpan<?> parent) {
        return traceContext.isChildOf(parent.traceContext);
    }

    @Override
    public Span createSpan() {
        return createSpan(traceContext.getClock().getEpochMicros());
    }

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

    protected void onStart() {
        this.finished = false;
    }

    public void end() {
        end(traceContext.getClock().getEpochMicros());
    }

    public final void end(long epochMicros) {
        if (!finished) {
            this.finished = true;
            this.duration = (epochMicros - timestamp) / AbstractSpan.MS_IN_MICROS;
            if (name.length() == 0) {
                name.append("unnamed");
            }
            doEnd(epochMicros);
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

    public void markLifecycleManagingThreadSwitchExpected() {
        isLifecycleManagingThreadSwitch = true;
    }

    @Override
    public T activate() {
        if (isLifecycleManagingThreadSwitch) {
            // This serves two goals:
            // 1. resets the lifecycle management flag, so that the executing thread will remain in charge until set otherwise
            // by setting this flag once more
            // 2. reading this volatile field when span is activated on a new thread ensures proper visibility of other span data
            isLifecycleManagingThreadSwitch = false;
        }
        return super.activate();
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
        if (isLifecycleManagingThreadSwitch) {
            return tracer.wrapRunnable(runnable, this);
        } else {
            return tracer.wrapRunnable(runnable, traceContext);
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
    public <V> Callable<V> withActive(Callable<V> callable) {
        if (isLifecycleManagingThreadSwitch) {
            return tracer.wrapCallable(callable, this);
        } else {
            return tracer.wrapCallable(callable, traceContext);
        }
    }

    public void setStartTimestamp(long epochMicros) {
        timestamp = epochMicros;
    }

}
