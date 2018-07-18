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

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.sampling.Sampler;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
public class Transaction extends AbstractSpan implements AutoCloseable {

    public static final String TYPE_REQUEST = "request";

    /**
     * This counter helps to assign the spans with sequential IDs
     */
    private final AtomicInteger spanIdCounter = new AtomicInteger();
    private volatile int maxSpans;
    private volatile boolean async;

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    private final Context context = new Context();
    private final List<Span> spans = new ArrayList<Span>();
    /**
     * A mark captures the timing of a significant event during the lifetime of a transaction. Marks are organized into groups and can be set by the user or the agent.
     */
    private final Map<String, Object> marks = new ConcurrentHashMap<>();
    private final SpanCount spanCount = new SpanCount();
    /**
     * UUID for the transaction, referred by its spans
     * (Required)
     */
    private final TransactionId id = new TransactionId();
    @Nullable
    private transient ElasticApmTracer tracer;
    /**
     * The result of the transaction. HTTP status code for HTTP-related transactions.
     */
    @Nullable
    private String result;
    /**
     * Keyword of specific relevance in the service's domain (eg: 'request', 'backgroundjob', etc)
     * (Required)
     */
    @Nullable
    private String type;
    /**
     * Transactions that are 'sampled' will include all available information. Transactions that are not sampled will not have 'spans' or 'context'. Defaults to true.
     */
    private boolean noop;
    
    public Transaction start(ElasticApmTracer tracer, @Nullable String traceParentHeader, long startTimestampNanos, Sampler sampler) {
        this.tracer = tracer;
        this.async = false;
        if (tracer != null && tracer.getConfig(CoreConfiguration.class) != null) {
            maxSpans = tracer.getConfig(CoreConfiguration.class).getTransactionMaxSpans();
        }
        if (traceParentHeader != null) {
            traceContext.asChildOf(traceParentHeader);
        } else {
            traceContext.asRootSpan(sampler);
        }

        this.duration = startTimestampNanos;
        this.timestamp = System.currentTimeMillis();
        this.id.setToRandomValue();
        this.noop = false;
        return this;
    }

    public Transaction startNoop(ElasticApmTracer tracer) {
        this.name.append("noop");
        this.async = false;
        this.tracer = tracer;
        this.noop = true;
        return this;
    }

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    public Context getContext() {
        return context;
    }

    /**
     * Returns the context and ensures visibility when accessed from a different thread.
     *
     * @return the transaction context
     * @see #getContext()
     */
    public Context getContextEnsureVisibility() {
        synchronized (this) {
            return context;
        }
    }

    /**
     * UUID for the transaction, referred by its spans
     * (Required)
     */
    public TransactionId getId() {
        return id;
    }

    public Transaction withName(@Nullable String name) {
        if (!isSampled()) {
            return this;
        }
        setName(name);
        return this;
    }

    /**
     * The result of the transaction. HTTP status code for HTTP-related transactions.
     */
    @Nullable
    public String getResult() {
        return result;
    }

    /**
     * The result of the transaction. HTTP status code for HTTP-related transactions.
     */
    public Transaction withResult(@Nullable String result) {
        if (!isSampled()) {
            return this;
        }
        this.result = result;
        return this;
    }

    @Deprecated
    public List<Span> getSpans() {
        return spans;
    }

    @Deprecated
    public Transaction addSpan(Span span) {
        if (!isSampled()) {
            return this;
        }
        synchronized (this) {
            spans.add(span);
        }
        return this;
    }

    /**
     * Keyword of specific relevance in the service's domain (eg: 'request', 'backgroundjob', etc)
     * (Required)
     */
    @Nullable
    public String getType() {
        return type;
    }

    /**
     * Keyword of specific relevance in the service's domain (eg: 'request', 'backgroundjob', etc)
     * (Required)
     */
    public void setType(@Nullable String type) {
        if (!isSampled()) {
            return;
        }
        this.type = type;
    }

    public void addTag(String key, String value) {
        if (!isSampled()) {
            return;
        }
        getContext().getTags().put(key, value);
    }

    public void setUser(String id, String email, String username) {
        if (!isSampled()) {
            return;
        }
        getContext().getUser().withId(id).withEmail(email).withUsername(username);
    }

    public void end() {
        end(System.nanoTime(), !async);
    }
    
    public void setAsync(boolean async) {
        this.async = async;
    }
    
    /**
     * Creates span bound to the transaction
     */
    public Span createSpan() {
        return createSpan(null, System.nanoTime());
    }
    
    /**
     * Creates span bound to the transaction
     */
    public Span createSpan(@Nullable Span parentSpan, long nanoTime) {
        if (tracer == null) {
            return null;
        }
        Span span = tracer.createSpan();
        final boolean dropped;
        if (isTransactionSpanLimitReached()) {
            // TODO only drop leaf spans
            dropped = true;
            spanCount.getDropped().increment();
        } else {
            dropped = false;
        }
        spanCount.increment();
        span.start(tracer, this, parentSpan, nanoTime, dropped);
        span.setAsync(async);
        return span;
    }
    
    private boolean isTransactionSpanLimitReached() {
        return maxSpans <= spanCount.getTotal();
    }
    
    public void end(long nanoTime, boolean releaseActiveTransaction) {
        this.duration = (nanoTime - duration) / ElasticApmTracer.MS_IN_NANOS;
        if (!isSampled()) {
            context.resetState();
        }
        if (this.tracer != null) {
            this.tracer.endTransaction(this, releaseActiveTransaction);
        }
    }

    @Override
    public void close() {
        end();
    }

    public Transaction withType(@Nullable String type) {
        this.type = type;
        return this;
    }

    /**
     * A mark captures the timing of a significant event during the lifetime of a transaction. Marks are organized into groups and can be set by the user or the agent.
     */
    public Map<String, Object> getMarks() {
        return marks;
    }

    public SpanCount getSpanCount() {
        return spanCount;
    }


    int getNextSpanId() {
        return spanIdCounter.incrementAndGet();
    }

    @Override
    public void resetState() {
        super.resetState();
        context.resetState();
        id.resetState();
        result = null;
        spans.clear();
        type = null;
        marks.clear();
        spanCount.resetState();
        tracer = null;
        spanIdCounter.set(0);
        noop = false;
        traceContext.resetState();
    }

    public void recycle() {
        if (tracer != null) {
            tracer.recycle(this);
        }
    }

    public boolean isNoop() {
        return noop;
    }

    @Override
    public String toString() {
        return String.format("'%s' %s", name, id);
    }
}
