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
import co.elastic.apm.impl.stacktrace.Stacktrace;
import co.elastic.apm.objectpool.Recyclable;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import static co.elastic.apm.impl.ElasticApmTracer.MS_IN_NANOS;

public class Span extends AbstractSpan implements Recyclable, AutoCloseable {

    /**
     * Any other arbitrary data captured by the agent, optionally provided by the user
     */
    private final SpanContext context = new SpanContext();
    /**
     * List of stack frames with variable attributes (eg: lineno, filename, etc)
     */
    private final List<Stacktrace> stacktrace = new ArrayList<Stacktrace>();
    /**
     * The locally unique ID of the span.
     */
    @Deprecated
    private final SpanId id = new SpanId();
    @Nullable
    private transient ElasticApmTracer tracer;
    /**
     * The locally unique ID of the parent of the span.
     */
    @Deprecated
    private final SpanId parent = new SpanId();
    /**
     * Offset relative to the transaction's timestamp identifying the start of the span, in milliseconds
     * (Required)
     */
    private volatile double start;
    /**
     * Keyword of specific relevance in the service's domain (eg: 'db.postgresql.query', 'template.erb', etc)
     * (Required)
     */
    @Nullable
    private volatile String type;
    
    private volatile Span parentSpan;

    @Nullable
    private volatile Transaction transaction;

    public Span start(ElasticApmTracer tracer, Transaction transaction, @Nullable Span parentSpan, long nanoTime, boolean dropped) {
        this.tracer = tracer;
        this.transaction = transaction;
        this.id.setLong(transaction.getNextSpanId());
        if (parentSpan != null) {
            this.parent.copyFrom(parentSpan.getId());
            this.traceContext.asChildOf(parentSpan.getTraceContext());
        } else {
            this.traceContext.asChildOf(transaction.getTraceContext());
        }
        if (dropped) {
            traceContext.setSampled(false);
        }
        if (traceContext.isSampled()) {
            start = (nanoTime - transaction.getDuration()) / MS_IN_NANOS;
            duration = nanoTime;
            timestamp = System.currentTimeMillis();
        }
        this.parentSpan = parentSpan;
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

    /**
     * List of stack frames with variable attributes (eg: lineno, filename, etc)
     */
    public List<Stacktrace> getStacktrace() {
        return stacktrace;
    }

    /**
     * Offset relative to the transaction's timestamp identifying the start of the span, in milliseconds
     * (Required)
     */
    public double getStart() {
        return start;
    }

    /**
     * Keyword of specific relevance in the service's domain (eg: 'db.postgresql.query', 'template.erb', etc)
     * (Required)
     */
    @Nullable
    public String getType() {
        return type;
    }

    /**
     * Keyword of specific relevance in the service's domain (eg: 'db.postgresql.query', 'template.erb', etc)
     * (Required)
     */
    public void setType(@Nullable String type) {
        withType(type);
    }

    public void end() {
        end(System.nanoTime(), true);
    }

    public void end(long nanoTime, boolean releaseActiveSpan) {
        this.transaction.setCurrentSpan(this.parentSpan);
        if (isSampled()) {
            this.duration = (nanoTime - duration) / MS_IN_NANOS;
        }
        if (this.tracer != null) {
            this.tracer.endSpan(this, releaseActiveSpan);
        }
    }

    @Override
    public void close() {
        end();
    }

    public Span withType(@Nullable String type) {
        if (!isSampled()) {
            return this;
        }
        this.type = type;
        return this;
    }

    @Override
    public void resetState() {
        super.resetState();
        id.resetState();
        context.resetState();
        parent.resetState();
        stacktrace.clear();
        start = 0;
        type = null;
        tracer = null;
        transaction = null;
        traceContext.resetState();
    }

    @Nullable
    public Transaction getTransaction() {
        return transaction;
    }

    public void recycle() {
        if (tracer != null) {
            tracer.recycle(this);
        }
    }

    @Override
    public String toString() {
        return String.format("'%s' %s:%s", name, transaction != null ? transaction.getId() : null, id.asLong());
    }
}
