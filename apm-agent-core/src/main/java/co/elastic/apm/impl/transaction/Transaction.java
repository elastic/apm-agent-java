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
import co.elastic.apm.impl.context.TransactionContext;
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
public class Transaction extends AbstractSpan<Transaction> {

    public static final String TYPE_REQUEST = "request";

    /**
     * This counter helps to assign the spans with sequential IDs
     */
    private final AtomicInteger spanIdCounter = new AtomicInteger();

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    private final TransactionContext context = new TransactionContext();
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

    /**
     * The result of the transaction. HTTP status code for HTTP-related transactions.
     */
    @Nullable
    private String result;
    /**
     * Keyword of specific relevance in the service's domain (eg:, etc)
     * (Required)
     */
    @Nullable
    private String type;
    /**
     * Transactions that are 'sampled' will include all available information. Transactions that are not sampled will not have 'spans' or 'context'. Defaults to true.
     */
    private boolean noop;

    public Transaction(ElasticApmTracer tracer) {
        super(tracer);
    }

    public Transaction start(@Nullable String traceParentHeader, long epochMicros, Sampler sampler) {
        if (traceParentHeader == null || !traceContext.asChildOf(traceParentHeader)) {
            traceContext.asRootSpan(sampler);
        }
        this.timestamp = clock.init();
        if (epochMicros >= 0) {
            this.timestamp = epochMicros;
        }
        this.id.setToRandomValue();
        this.noop = false;
        return this;
    }

    public Transaction startNoop() {
        this.name.append("noop");
        this.noop = true;
        return this;
    }

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    public TransactionContext getContext() {
        return context;
    }

    /**
     * Returns the context and ensures visibility when accessed from a different thread.
     *
     * @return the transaction context
     * @see #getContext()
     */
    public TransactionContext getContextEnsureVisibility() {
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

    public List<Span> getSpans() {
        // ensures visibility; lock is not likely to have contention
        synchronized (this) {
            return spans;
        }
    }

    public Transaction addSpan(Span span) {
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

    @Override
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

    @Override
    public void end() {
        end(clock.getEpochMicros());
    }

    @Override
    public void end(long epochMicros) {
        this.duration = (epochMicros - timestamp) / AbstractSpan.MS_IN_MICROS;
        if (!isSampled()) {
            context.resetState();
        }
        final ElasticApmTracer tracer = this.tracer;
        for (Span span : spans) {
            span.onTransactionEnd();
        }
        tracer.endTransaction(this);
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
        spanIdCounter.set(0);
        noop = false;
        traceContext.resetState();
    }

    @Override
    public Transaction getTransaction() {
        return this;
    }

    public void recycle() {
        tracer.recycle(this);
    }

    public boolean isNoop() {
        return noop;
    }

    @Override
    public String toString() {
        return String.format("'%s' %s", name, id);
    }
}
