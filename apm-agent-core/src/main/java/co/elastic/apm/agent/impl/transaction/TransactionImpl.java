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

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.configuration.SpanConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.baggage.BaggageImpl;
import co.elastic.apm.agent.impl.baggage.W3CBaggagePropagation;
import co.elastic.apm.agent.impl.context.ResponseImpl;
import co.elastic.apm.agent.impl.context.TransactionContextImpl;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.tracer.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.Timer;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.util.ResultUtil;
import co.elastic.apm.agent.util.KeyListConcurrentHashMap;
import org.HdrHistogram.WriterReaderPhaser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static co.elastic.apm.agent.configuration.CoreConfigurationImpl.TraceContinuationStrategy.RESTART;
import static co.elastic.apm.agent.configuration.CoreConfigurationImpl.TraceContinuationStrategy.RESTART_EXTERNAL;

/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
public class TransactionImpl extends AbstractSpanImpl<TransactionImpl> implements Transaction<TransactionImpl> {

    /**
     * Mutable labels instance used when reporting transaction metrics.
     * This is a field to prevent allocations.
     */
    private final Labels.Mutable labelsMutable = Labels.Mutable.of();

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    private final TransactionContextImpl context = new TransactionContextImpl();
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
    private final CoreConfigurationImpl coreConfig;
    private final SpanConfiguration spanConfig;

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

    private int maxSpans;

    private boolean spanCompressionEnabled;

    private long spanCompressionExactMatchMaxDurationUs;

    private long spanCompressionSameKindMaxDurationUs;

    @Nullable
    private String frameworkName;

    private boolean frameworkNameSetByUser;

    @Nullable
    private String frameworkVersion;

    @Nullable
    private Throwable pendingException;

    private final ArrayList<IdImpl> profilingCorrelationStackTraceIds = new ArrayList<>();

    /**
     * Faas
     * <p>
     * If a services is executed as a serverless function (function as a service), FaaS-specific information can be collected within this object.
     */
    private final FaasImpl faas = new FaasImpl();

    private final AtomicBoolean wasActivated = new AtomicBoolean();

    @Override
    public TransactionImpl getParentTransaction() {
        return this;
    }

    public TransactionImpl(ElasticApmTracer tracer) {
        super(tracer);
        coreConfig = tracer.getConfig(CoreConfigurationImpl.class);
        spanConfig = tracer.getConfig(SpanConfiguration.class);
    }

    public <T> TransactionImpl startRoot(long epochMicros, Sampler sampler, BaggageImpl baggage) {
        this.baggage = baggage;
        traceContext.asRootSpan(sampler);
        onTransactionStart(epochMicros);
        return this;
    }

    public <H, C> TransactionImpl start(
        @Nullable C parent,
        HeaderGetter<H, C> headerGetter,
        long epochMicros,
        Sampler sampler,
        BaggageImpl baseBaggage
    ) {
        if (parent == null) {
            return startRoot(epochMicros, sampler, baseBaggage);
        }
        BaggageImpl.Builder baggageBuilder = baseBaggage.toBuilder();
        W3CBaggagePropagation.parse(parent, headerGetter, baggageBuilder);
        this.baggage = baggageBuilder.build();
        CoreConfigurationImpl.TraceContinuationStrategy traceContinuationStrategy = coreConfig.getTraceContinuationStrategy();
        boolean restartTrace = false;
        if (traceContinuationStrategy.equals(RESTART)) {
            restartTrace = true;
        } else if (traceContinuationStrategy.equals(RESTART_EXTERNAL)) {
            restartTrace = !TraceState.includesElasticVendor(headerGetter, parent);
        }
        if (restartTrace) {
            // need to add a span link
            addSpanLink(headerGetter, parent);
            traceContext.asRootSpan(sampler);
        } else {
            boolean valid = traceContext.asChildOf(parent, headerGetter);
            if (!valid) {
                traceContext.asRootSpan(sampler);
            }
        }

        onTransactionStart(epochMicros);
        return this;
    }

    private void onTransactionStart(long epochMicros) {
        maxSpans = coreConfig.getTransactionMaxSpans();
        spanCompressionEnabled = spanConfig.isSpanCompressionEnabled();
        spanCompressionExactMatchMaxDurationUs = spanConfig.getSpanCompressionExactMatchMaxDuration().getMicros();
        spanCompressionSameKindMaxDurationUs = spanConfig.getSpanCompressionSameKindMaxDuration().getMicros();

        if (epochMicros >= 0) {
            setStartTimestamp(epochMicros);
        } else {
            setStartTimestampNow();
        }
        onAfterStart();
    }

    public TransactionImpl startNoop() {
        this.name.append("noop");
        this.noop = true;
        onAfterStart();
        return this;
    }

    @Override
    public TransactionImpl activate() {
        // why first the get() and then the getAndSet() here? get() is a cheap operation (volatile read)
        // while getAndSet() is an atomic operation with the corresponding performance penalty
        // therefore we only pay the price of the atomic operation on the first activation
        if (!wasActivated.get()) {
            if (!wasActivated.getAndSet(true)) {
                onFirstActivation();
            }
        }
        return super.activate();
    }

    private void onFirstActivation() {
        String execution = getFaas().getExecution();
        if (execution != null && !execution.isEmpty()) {
            tracer.reportPartialTransaction(this);
        }
    }

    @Override
    public TransactionContextImpl getContext() {
        return context;
    }

    /**
     * Returns the context and ensures visibility when accessed from a different thread.
     *
     * @return the transaction context
     * @see #getContext()
     */
    public TransactionContextImpl getContextEnsureVisibility() {
        synchronized (this) {
            return context;
        }
    }

    /**
     * The result of the transaction. HTTP status code for HTTP-related transactions.
     */
    @Nullable
    public String getResult() {
        return result;
    }

    @Override
    public TransactionImpl withResultIfUnset(@Nullable String result) {
        if (this.result == null) {
            this.result = result;
        }
        return this;
    }

    @Override
    public TransactionImpl withResult(@Nullable String result) {
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

        if (outcomeNotSet()) {
            // set outcome from HTTP status if not already set
            ResponseImpl response = getContext().getResponse();
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

    public void captureDroppedSpan(SpanImpl span) {
        if (span.isSampled()) {
            spanCount.getDropped().incrementAndGet();
        }
        droppedSpanStats.captureDroppedSpan(span);
    }

    public DroppedSpanStats getDroppedSpanStats() {
        return droppedSpanStats;
    }


    public boolean checkSkipChildSpanCreation() {
        boolean drop = spanCount.isSpanLimitReached(maxSpans);
        if (drop) {
            // when dropping, the caller is expected to optimize and avoid span creation. As a consequence we have
            // to artificially increase those counters to make it as if the span was actually created and dropped
            // before reporting
            spanCount.getTotal().incrementAndGet();
            spanCount.getDropped().incrementAndGet();
        }
        return drop;
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
        noop = false;
        maxSpans = 0;
        spanCompressionEnabled = false;
        spanCompressionExactMatchMaxDurationUs = 0L;
        spanCompressionSameKindMaxDurationUs = 0L;
        frameworkName = null;
        frameworkVersion = null;
        faas.resetState();
        wasActivated.set(false);
        pendingException = null;
        recycleProfilingCorrelationStackTraceIds();
        // don't clear timerBySpanTypeAndSubtype map (see field-level javadoc)
    }

    private void recycleProfilingCorrelationStackTraceIds() {
        for (IdImpl toRecycle : profilingCorrelationStackTraceIds) {
            tracer.recycleProfilingCorrelationStackTraceId(toRecycle);
        }
        if (profilingCorrelationStackTraceIds.size() > 100) {
            profilingCorrelationStackTraceIds.clear();
            //trim overly big lists
            profilingCorrelationStackTraceIds.trimToSize();
        } else {
            profilingCorrelationStackTraceIds.clear();
        }
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void ignoreTransaction() {
        noop = true;
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

    @Override
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

    @Override
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
    public FaasImpl getFaas() {
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
    public CharSequence getNameForSerialization() {
        WildcardMatcher match = WildcardMatcher.anyMatch(coreConfig.getTransactionNameGroups(), this.name);
        if (match != null) {
            this.name.setLength(0);
            this.name.append(match);
        }
        return super.getNameForSerialization();
    }

    @Override
    protected TransactionImpl thiz() {
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
            labelsMutable.resetState();
            labelsMutable.serviceName(getTraceContext().getServiceName())
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
                                labelsMutable.spanType(spanType).spanSubType(subtype);
                                metricRegistry.updateTimer("span.self_time", labelsMutable, timer.getTotalTimeUs(), timer.getCount());
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


    @Override
    public void setPendingTransactionException(@Nullable Throwable exception) {
        this.pendingException = exception;
    }

    @Nullable
    @Override
    public Throwable getPendingTransactionException() {
        return this.pendingException;
    }

    public void addProfilerCorrelationStackTrace(IdImpl idToCopy) {
        IdImpl id = tracer.createProfilingCorrelationStackTraceId();
        id.copyFrom(idToCopy);
        synchronized (profilingCorrelationStackTraceIds) {
            this.profilingCorrelationStackTraceIds.add(id);
        }
    }

    /**
     * Returns the list of stacktrace-IDs from the profiler associated with this transaction
     * To protect agains concurrent modifications, consumers must synchronize on the returned list.
     */
    public List<IdImpl> getProfilingCorrelationStackTraceIds() {
        return profilingCorrelationStackTraceIds;
    }
}
