/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.impl.transaction;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.SpanContext;
import co.elastic.apm.objectpool.Recyclable;

import javax.annotation.Nullable;

public class Span extends AbstractSpan<Span> implements Recyclable {

    /**
     * Any other arbitrary data captured by the agent, optionally provided by the user
     */
    private final SpanContext context = new SpanContext();
    /**
     * The locally unique ID of the span.
     */
    @Deprecated
    private final SpanId id = new SpanId();
    /**
     * The locally unique ID of the parent of the span.
     */
    @Deprecated
    private final SpanId parent = new SpanId();
    @Nullable
    private Throwable stacktrace;
    /**
     * Offset relative to the transaction's timestamp identifying the start of the span, in milliseconds
     * (Required)
     */
    // TODO remove after https://github.com/elastic/apm-server/issues/1340 has been implemented in APM Server
    @Deprecated
    private volatile double start;

    @Nullable
    private volatile Transaction transaction;

    public Span(ElasticApmTracer tracer) {
        super(tracer);
    }

    public Span start(Transaction transaction, @Nullable Span parentSpan, long epochMicros, boolean dropped) {
        this.transaction = transaction;
        this.clock.init(transaction.clock);
        if (parentSpan != null) {
            this.parent.copyFrom(parentSpan.getId());
            start(parentSpan.getTraceContext(), epochMicros, dropped);
        } else {
            start(transaction.getTraceContext(), epochMicros, dropped);
        }
        // TODO remove after dropping support for intake v1
        this.id.setLong(transaction.getNextSpanId());
        this.start = (timestamp - transaction.timestamp) / MS_IN_MICROS;
        return this;
    }

    public Span start(TraceContext parent) {
        final long epochMicros = this.clock.init();
        return start(parent, epochMicros, false);
    }

    public Span start(TraceContext parent, long epochMicros) {
        this.clock.init();
        return start(parent, epochMicros, false);
    }

    private Span start(TraceContext parent, long epochMicros, boolean dropped) {
        onStart();
        this.traceContext.asChildOf(parent);
        if (dropped) {
            traceContext.setRecorded(false);
        }
        if (traceContext.isSampled()) {
            timestamp = epochMicros;
        }
        return this;
    }

    public Span startNoop() {
        onStart();
        return this;
    }

    /**
     * The locally unique ID of the span.
     */
    @Deprecated
    public SpanId getId() {
        return id;
    }

    /**
     * Any other arbitrary data captured by the agent, optionally provided by the user
     */
    public SpanContext getContext() {
        return context;
    }

    public Span withName(@Nullable String name) {
        setName(name);
        return this;
    }

    /**
     * The locally unique ID of the parent of the span.
     */
    @Deprecated
    public SpanId getParent() {
        return parent;
    }

    @Nullable
    public Throwable getStacktrace() {
        return stacktrace;
    }

    /**
     * Offset relative to the transaction's timestamp identifying the start of the span, in milliseconds
     * (Required)
     */
    public double getStart() {
        return start;
    }

    @Override
    public void doEnd(long epochMicros) {
        this.tracer.endSpan(this);
    }

    @Override
    public void resetState() {
        super.resetState();
        id.resetState();
        context.resetState();
        parent.resetState();
        stacktrace = null;
        start = 0;
        transaction = null;
    }

    /**
     * Returns the {@link Transaction} which this span belongs to.
     * <p>
     * Can return {@code null} in case the span has been started via
     * {@link Span#start(TraceContext)} or {@link Span#start(TraceContext, long)}
     * or in case the span has not been {@linkplain Span#start(Transaction, Span, long, boolean) started} yet.
     * </p>
     *
     * @return the transaction which belongs to this span
     */
    @Nullable
    public Transaction getTransaction() {
        return transaction;
    }

    @Override
    public void addTag(String key, String value) {
        context.getTags().put(key, value);
    }

    public void recycle() {
        tracer.recycle(this);
    }

    @Override
    public String toString() {
        return String.format("'%s' %s:%s", name, transaction != null ? transaction.getId() : null, id.asLong());
    }

    public Span withStacktrace(Throwable stacktrace) {
        this.stacktrace = stacktrace;
        return this;
    }
}
