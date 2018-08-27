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

import static co.elastic.apm.impl.ElasticApmTracer.MS_IN_NANOS;

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
    private volatile double start;

    @Nullable
    private volatile Transaction transaction;

    public Span(ElasticApmTracer tracer) {
        super(tracer);
    }

    public Span start(Transaction transaction, @Nullable Span parentSpan, long nanoTime, boolean dropped) {
        this.transaction = transaction;
        this.id.setLong(transaction.getNextSpanId());
        if (parentSpan != null) {
            this.parent.copyFrom(parentSpan.getId());
            this.traceContext.asChildOf(parentSpan.getTraceContext());
        } else {
            this.traceContext.asChildOf(transaction.getTraceContext());
        }
        if (dropped) {
            traceContext.setRecorded(false);
        }
        if (traceContext.isSampled()) {
            start = (nanoTime - transaction.getDuration()) / MS_IN_NANOS;
            startTimestampNanos = nanoTime;
            timestamp = System.currentTimeMillis();
        }
        return this;
    }

    public Span startNoop() {
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
    public void end() {
        end(System.nanoTime());
    }

    @Override
    public void end(long nanoTime) {
        if (isSampled()) {
            this.duration = (nanoTime - startTimestampNanos) / MS_IN_NANOS;
        }
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
