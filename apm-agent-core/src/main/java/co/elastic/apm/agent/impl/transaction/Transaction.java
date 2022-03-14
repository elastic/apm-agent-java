/*
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
 */
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpanConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.Timer;
import co.elastic.apm.agent.util.KeyListConcurrentHashMap;
import org.HdrHistogram.WriterReaderPhaser;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
public class Transaction extends AbstractSpan<Transaction> {

    private static final ThreadLocal<Labels.Mutable> labelsThreadLocal = new ThreadLocal<Labels.Mutable>() {
        @Override
        protected Labels.Mutable initialValue() {
            return Labels.Mutable.of();
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
    private final DroppedSpanStats droppedSpanStats = new DroppedSpanStats();
    /**
     * type: subtype: timer
     * <p>
     * This map is not cleared when the transaction is recycled.
     * Instead, it accumulates span types and subtypes over time.
     * When tracking the metrics, the timers are reset and only those with a count > 0 are examined.
     * That is done in order to minimize {@link java.util.Map.Entry} garbage.
     * </p>
     */
    private final KeyListConcurrentHashMap<String, KeyListConcurrentHashMap<String, Timer>> timerBySpanTypeAndSubtype = new KeyListConcurrentHashMap<>();
    private final WriterReaderPhaser phaser = new WriterReaderPhaser();

    /**
     * The result of the transaction. HTTP status code for HTTP-related
     * transactions.
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

    private int maxSpans;

    private boolean spanCompressionEnabled;

    private long spanCompressionExactMatchMaxDurationUs;

    private long spanCompressionSameKindMaxDurationUs;

    @Nullable
    private String frameworkName;

    private boolean frameworkNameSetByUser;

    @Nullable
    private String frameworkVersion;

    /**
     * Faas
     * <p>
     * If a services is executed as a serverless function (function as a service), FaaS-specific information can be collected within this object.
     */
    private final Faas faas = new Faas();

    @Override
    public Transaction getTransaction() {
        return this;
    }

    public Transaction(ElasticApmTracer tracer) {
        super(tracer);
    }

    public <T> Transaction start(TraceContext.ChildContextCreator<T> childContextCreator, @Nullable T parent, long epochMicros,
                                 Sampler sampler, @Nullable ClassLoader initiatingClassLoader) {
        traceContext.setApplicationClassLoader(initiatingClassLoader);
        boolean startedAsChild = parent != null && childContextCreator.asChildOf(traceContext, parent);
        onTransactionStart(startedAsChild, epochMicros, sampler);
        return this;
    }

    public <T, A> Transaction start(TraceContext.ChildContextCreatorTwoArg<T, A> childContextCreator, @Nullable T parent, A arg,
                                    long epochMicros, Sampler sampler, @Nullable ClassLoader initiatingClassLoader) {
        traceContext.setApplicationClassLoader(initiatingClassLoader);
        boolean startedAsChild = childContextCreator.asChildOf(traceContext, parent, arg);
        onTransactionStart(startedAsChild, epochMicros, sampler);
        return this;
    }

    private void onTransactionStart(boolean startedAsChild, long epochMicros, Sampler sampler) {
        maxSpans = tracer.getConfig(CoreConfiguration.class).getTransactionMaxSpans();
        spanCompressionEnabled = tracer.getConfig(SpanConfiguration.class).isSpanCompressionEnabled();
        spanCompressionExactMatchMaxDurationUs = tracer.getConfig(SpanConfiguration.class).getSpanCompressionExactMatchMaxDuration().getMicros();
        spanCompressionSameKindMaxDurationUs = tracer.getConfig(SpanConfiguration.class).getSpanCompressionSameKindMaxDuration().getMicros();
        if (!startedAsChild) {
            traceContext.asRootSpan(sampler);
        }
        if (epochMicros >= 0) {
            setStartTimestamp(epochMicros);
        } else {
            setStartTimestampNow();
        }
        onAfterStart();
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
    @Override
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
     * The result of the transaction. HTTP status code for HTTP-related
     * transactions. This sets the result only if it is not already set. should be
     * used for instrumentations
     */
    public Transaction withResultIfUnset(@Nullable String result) {
        if (this.result == null) {
            this.result = result;
        }
        return this;
    }

    /**
     * The result of the transaction. HTTP status code for HTTP-related
     * transactions. This sets the result regardless of an already existing value.
     * should be used for user defined results
     */
    public Transaction withResult(@Nullable String result) {
        this.result = result;
        return this;
    }

    public void setUser(String id, String email, String username, String domain) {
        if (!isSampled()) {
            return;
        }
        getContext().getUser().withDomain(domain).withId(id).withEmail(email).withUsername(username);
    }

    @Override
    public void beforeEnd(long epochMicros) {
        if (!isSampled()) {
            context.resetState();
        }
        if (type == null) {
            type = "custom";
        }

        if (outcomeNotSet()) {
            // set outcome from HTTP status if not already set
            Response response = getContext().getResponse();
            Outcome outcome;

            int httpStatus = response.getStatusCode();
            if (httpStatus > 0) {
                outcome = ResultUtil.getOutcomeByHttpServerStatus(httpStatus);
            } else {
                outcome = hasCapturedExceptions() ? Outcome.FAILURE : Outcome.SUCCESS;
            }
            withOutcome(outcome);
        }

        context.onTransactionEnd();
        incrementTimer("app", null, getSelfDuration());
    }



    @Override
    protected void afterEnd() {
        trackMetrics();
        this.tracer.endTransaction(this);
    }

    public SpanCount getSpanCount() {
        return spanCount;
    }

    public void captureDroppedSpan(Span span) {
        if (span.isSampled()) {
            spanCount.getDropped().incrementAndGet();
        }
        droppedSpanStats.captureDroppedSpan(span);
    }

    public DroppedSpanStats getDroppedSpanStats() {
        return droppedSpanStats;
    }

    boolean isSpanLimitReached() {
        return getSpanCount().isSpanLimitReached(maxSpans);
    }

    public KeyListConcurrentHashMap<String, KeyListConcurrentHashMap<String, Timer>> getTimerBySpanTypeAndSubtype() {
        return timerBySpanTypeAndSubtype;
    }

    @Override
    public void resetState() {
        super.resetState();
        context.resetState();
        result = null;
        spanCount.resetState();
        droppedSpanStats.resetState();
        type = null;
        noop = false;
        maxSpans = 0;
        spanCompressionEnabled = false;
        spanCompressionExactMatchMaxDurationUs = 0L;
        spanCompressionSameKindMaxDurationUs = 0L;
        frameworkName = null;
        frameworkVersion = null;
        faas.resetState();
        // don't clear timerBySpanTypeAndSubtype map (see field-level javadoc)
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

    public void addCustomContext(String key, String value) {
        if (isSampled()) {
            getContext().addCustom(key, value);
        }
    }

    public void addCustomContext(String key, Number value) {
        if (isSampled()) {
            getContext().addCustom(key, value);
        }
    }

    public void addCustomContext(String key, Boolean value) {
        if (isSampled()) {
            getContext().addCustom(key, value);
        }
    }

    @Override
    public String toString() {
        return String.format("'%s' %s (%s)", name, traceContext, Integer.toHexString(System.identityHashCode(this)));
    }

    @Override
    public void incrementReferences() {
        super.incrementReferences();
    }

    @Override
    protected void recycle() {
        tracer.recycle(this);
    }

    public void setFrameworkName(@Nullable String frameworkName) {
        if (frameworkNameSetByUser) {
            return;
        }
        this.frameworkName = frameworkName;
    }

    public void setUserFrameworkName(@Nullable String frameworkName) {
        if (frameworkName != null && frameworkName.isEmpty()) {
            this.frameworkName = null;
        } else {
            this.frameworkName = frameworkName;
        }
        this.frameworkNameSetByUser = true;
    }

    @Nullable
    public String getFrameworkName() {
        return this.frameworkName;
    }

    public void setFrameworkVersion(@Nullable String frameworkVersion) {
        this.frameworkVersion = frameworkVersion;
    }

    @Nullable
    public String getFrameworkVersion() {
        return this.frameworkVersion;
    }

    /**
     * Function as a Service (Faas)
     * <p>
     * If a services is executed as a serverless function (function as a service), FaaS-specific information can be collected within this object.
     */
    public Faas getFaas() {
        return faas;
    }

    public boolean isSpanCompressionEnabled() {
        return spanCompressionEnabled;
    }

    public long getSpanCompressionExactMatchMaxDurationUs() {
        return spanCompressionExactMatchMaxDurationUs;
    }

    public long getSpanCompressionSameKindMaxDurationUs() {
        return spanCompressionSameKindMaxDurationUs;
    }

    @Override
    protected Transaction thiz() {
        return this;
    }

    void incrementTimer(@Nullable String type, @Nullable String subtype, long duration) {
        long criticalValueAtEnter = phaser.writerCriticalSectionEnter();
        try {
            if (!collectBreakdownMetrics || type == null || finished) {
                return;
            }
            if (subtype == null) {
                subtype = "";
            }
            KeyListConcurrentHashMap<String, Timer> timersBySubtype = timerBySpanTypeAndSubtype.get(type);
            if (timersBySubtype == null) {
                timersBySubtype = new KeyListConcurrentHashMap<>();
                KeyListConcurrentHashMap<String, Timer> racyMap = timerBySpanTypeAndSubtype.putIfAbsent(type, timersBySubtype);
                if (racyMap != null) {
                    timersBySubtype = racyMap;
                }
            }
            Timer timer = timersBySubtype.get(subtype);
            if (timer == null) {
                timer = new Timer();
                Timer racyTimer = timersBySubtype.putIfAbsent(subtype, timer);
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
        } finally {
            phaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    private void trackMetrics() {
        try {
            phaser.readerLock();
            phaser.flipPhase();
            // timers are guaranteed to be stable now
            // - no concurrent updates possible as finished is true
            // - no other thread is running the incrementTimer method,
            //   as flipPhase only returns when all threads have exited that method

            final String type = getType();
            if (type == null) {
                return;
            }
            final Labels.Mutable labels = labelsThreadLocal.get();
            labels.resetState();
            labels.serviceName(getTraceContext().getServiceName())
                .serviceVersion(getTraceContext().getServiceVersion())
                .transactionName(name)
                .transactionType(type);
            final MetricRegistry metricRegistry = tracer.getMetricRegistry();
            long criticalValueAtEnter = metricRegistry.writerCriticalSectionEnter();
            try {
                if (collectBreakdownMetrics) {
                    List<String> types = timerBySpanTypeAndSubtype.keyList();
                    for (int i = 0; i < types.size(); i++) {
                        String spanType = types.get(i);
                        KeyListConcurrentHashMap<String, Timer> timerBySubtype = timerBySpanTypeAndSubtype.get(spanType);
                        List<String> subtypes = timerBySubtype.keyList();
                        for (int j = 0; j < subtypes.size(); j++) {
                            String subtype = subtypes.get(j);
                            final Timer timer = timerBySubtype.get(subtype);
                            if (timer.getCount() > 0) {
                                if (subtype.equals("")) {
                                    subtype = null;
                                }
                                labels.spanType(spanType).spanSubType(subtype);
                                metricRegistry.updateTimer("span.self_time", labels, timer.getTotalTimeUs(), timer.getCount());
                                timer.resetState();
                            }
                        }
                    }
                }
            } finally {
                metricRegistry.writerCriticalSectionExit(criticalValueAtEnter);
            }
        } finally {
            phaser.readerUnlock();
        }
    }
}
