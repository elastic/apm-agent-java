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
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.Timer;
import co.elastic.apm.agent.util.KeyListConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
public class Transaction extends AbstractSpan<Transaction> {

    private static final Logger logger = LoggerFactory.getLogger(Transaction.class);
    private static final ThreadLocal<Labels> labelsThreadLocal = new ThreadLocal<>() {
        @Override
        protected Labels initialValue() {
            return new Labels();
        }
    };

    public static final String TYPE_REQUEST = "request";

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    private final TransactionContext context = new TransactionContext();
    private final SpanCount spanCount = new SpanCount();
    private final KeyListConcurrentHashMap<String, Timer> spanTimings = new KeyListConcurrentHashMap<>();

    /**
     * The result of the transaction. HTTP status code for HTTP-related transactions.
     */
    @Nullable
    private String result;

    /**
     * Noop transactions won't be reported at all, in contrast to non-sampled transactions.
     */
    private boolean noop;

    /**
     * Keyword of specific relevance in the service's domain (eg:  'request', 'backgroundjob')
     * (Required)
     */
    @Nullable
    private volatile String type;

    public Transaction(ElasticApmTracer tracer) {
        super(tracer);
    }

    public <T> Transaction start(TraceContext.ChildContextCreator<T> childContextCreator, @Nullable T parent, long epochMicros, Sampler sampler) {
        if (parent == null || !childContextCreator.asChildOf(traceContext, parent)) {
            traceContext.asRootSpan(sampler);
        }
        if (epochMicros >= 0) {
            setStartTimestamp(epochMicros);
        } else {
            setStartTimestamp(traceContext.getClock().getEpochMicros());
        }
        onAfterStart();
        return this;
    }

    public Transaction startNoop() {
        this.name.append("noop");
        this.noop = true;
        onAfterStart();
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
     * Keyword of specific relevance in the service's domain (eg:  'request', 'backgroundjob')
     */
    public Transaction withType(@Nullable String type) {
        this.type = type;
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
    public void addLabel(String key, String value) {
        if (!isSampled()) {
            return;
        }
        getContext().addLabel(key, value);
    }

    @Override
    public void addLabel(String key, Number value) {
        context.addLabel(key, value);
    }

    @Override
    public void addLabel(String key, Boolean value) {
        context.addLabel(key, value);
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
        if (type == null) {
            type = "custom";
        }
        context.onTransactionEnd();
        trackMetrics();
        this.tracer.endTransaction(this);
    }

    public SpanCount getSpanCount() {
        return spanCount;
    }

    public KeyListConcurrentHashMap<String, Timer> getSpanTimings() {
        return spanTimings;
    }

    @Override
    public void resetState() {
        super.resetState();
        context.resetState();
        result = null;
        spanCount.resetState();
        noop = false;
        type = null;
    }

    public boolean isNoop() {
        return noop;
    }

    /**
     * Ignores this transaction, which makes it a noop so that it will not be reported to the APM Server.
     */
    public void ignoreTransaction() {
        noop = true;
    }

    @Nullable
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return String.format("'%s' %s", name, traceContext);
    }

    @Override
    public void incrementReferences() {
        super.incrementReferences();
    }

    public void decrementReferences() {
        final int referenceCount = this.references.decrementAndGet();
        logger.trace("decrement references to {} ({})", this, referenceCount);
        if (referenceCount == 0) {
            tracer.recycle(this);
        }
    }

    void incrementTimer(@Nullable String type, long duration) {
        if (type != null && !finished) {
            Timer timer = spanTimings.get(type);
            if (timer == null) {
                timer = new Timer();
                Timer racyTimer = spanTimings.putIfAbsent(type, timer);
                if (racyTimer != null) {
                    timer = racyTimer;
                }
            }
            timer.update(duration);
            if (finished) {
                // in case end()->trackMetrics() has been called concurrently
                // don't leak timers
                timer.resetState();
            }
        }
    }

    private void trackMetrics() {
        final String type = getType();
        if (type == null) {
            return;
        }
        final Labels labels = labelsThreadLocal.get();
        labels.resetState();
        labels.transactionName(name).transactionType(type);
        final KeyListConcurrentHashMap<String, Timer> spanTimings = getSpanTimings();
        List<String> keyList = spanTimings.keyList();
        for (int i = 0; i < keyList.size(); i++) {
            String spanType = keyList.get(i);
            final Timer timer = spanTimings.get(spanType);
            if (timer.getCount() > 0) {
                tracer.getMetricRegistry().timer("self_time", labels.spanType(spanType)).update(timer.getTotalTimeUs(), timer.getCount());
                timer.resetState();
            }
        }
        tracer.getMetricRegistry().timer("self_time", labels.spanType("transaction")).update(getSelfDuration());
        tracer.getMetricRegistry().timer("duration", labels.spanType("transaction")).update(getDuration());
    }
}
