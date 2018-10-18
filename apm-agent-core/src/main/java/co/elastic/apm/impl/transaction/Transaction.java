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

/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
public class Transaction extends AbstractSpan<Transaction> {

    public static final String TYPE_REQUEST = "request";

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    private final TransactionContext context = new TransactionContext();
    private final SpanCount spanCount = new SpanCount();

    /**
     * The result of the transaction. HTTP status code for HTTP-related transactions.
     */
    @Nullable
    private String result;

    /**
     * Transactions that are 'sampled' will include all available information. Transactions that are not sampled will not have 'spans' or 'context'. Defaults to true.
     */
    private boolean noop;

    public Transaction(ElasticApmTracer tracer) {
        super(tracer);
    }

    public Transaction start(@Nullable String traceParentHeader, long epochMicros, Sampler sampler) {
        onStart();
        if (traceParentHeader == null || !traceContext.asChildOf(traceParentHeader)) {
            traceContext.asRootSpan(sampler);
        }
        this.timestamp = clock.init();
        if (epochMicros >= 0) {
            this.timestamp = epochMicros;
        }
        this.noop = false;
        return this;
    }

    public Transaction startNoop() {
        onStart();
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
    public void doEnd(long epochMicros) {
        if (!isSampled()) {
            context.resetState();
        }
        this.tracer.endTransaction(this);
    }

    public SpanCount getSpanCount() {
        return spanCount;
    }

    @Override
    public void resetState() {
        super.resetState();
        context.resetState();
        result = null;
        spanCount.resetState();
        noop = false;
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
        return String.format("'%s' %s", name, traceContext);
    }
}
