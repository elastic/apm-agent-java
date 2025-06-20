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
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.baggage.BaggageImpl;
import co.elastic.apm.agent.impl.context.AbstractContextImpl;
import co.elastic.apm.agent.report.ReporterConfigurationImpl;
import co.elastic.apm.agent.sdk.internal.collections.LongList;
import co.elastic.apm.agent.sdk.internal.util.LoggerUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractSpanImpl<T extends AbstractSpanImpl<T>> extends AbstractRefCountedContext<T> implements Recyclable, AbstractSpan<T> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractSpanImpl.class);
    private static final Logger oneTimeDuplicatedEndLogger = LoggerUtils.logOnce(logger);
    private static final Logger oneTimeMaxSpanLinksLogger = LoggerUtils.logOnce(logger);

    protected static final double MS_IN_MICROS = TimeUnit.MILLISECONDS.toMicros(1);
    protected final TraceContextImpl traceContext;

    protected BaggageImpl baggage = BaggageImpl.EMPTY;

    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    protected final StringBuilder name = new StringBuilder();
    protected final boolean collectBreakdownMetrics;
    protected final AtomicLong timestamp = new AtomicLong();
    protected final AtomicLong endTimestamp = new AtomicLong();

    private ChildDurationTimer childDurations = new ChildDurationTimer();
    protected volatile boolean finished = true;
    private int namePriority = PRIORITY_DEFAULT;
    private boolean discardRequested = false;
    /**
     * Flag to mark a span as representing an exit event
     */
    private boolean isExit;
    /**
     * <p>
     * This use case for child ids is modifying parent/child relationships for profiler-inferred spans.
     * Inferred spans are sent after a profiling session ends (5s by default) and after stack traces have been processed into inferred spans.
     * Regular spans are sent right after the event (for example a DB call) has occurred.
     * The effect is that a regular span cannot have a {@link TraceContextImpl#parentId} pointing to an inferred span.
     * That is because the latter did not exist at the time the regular span has been created.
     * </p>
     * <p>
     * To work around this problem, inferred spans can point to their children.
     * The UI does an operation known as "transitive reduction".
     * What this does in this scenario is that it ignores the parent ID of a regular span if there's an inferred span
     * with a {@code child_id} for this span.
     * </p>
     * <pre>
     * ██████████████████████████████  transaction
     * ↑ ↑ parent_id
     * ╷ └──████████████████████       inferred span
     * ╷         ↓ child_id
     * └╶╶╶╶╶╶╶╶╶██████████            DB span
     *  parent_id
     *  (removed via transitive reduction)
     * </pre>
     */
    @Nullable
    @SuppressWarnings("JavadocReference") // for link to TraceContext#parentId
    private LongList childIds;

    /**
     * outcome set by span/transaction instrumentation
     */
    @Nullable
    private Outcome outcome;

    /**
     * outcome set by user explicitly
     */
    @Nullable
    private Outcome userOutcome = null;

    private boolean hasCapturedExceptions;

    @Nullable
    protected volatile String type;

    private volatile boolean sync = true;

    protected final SpanAtomicReference<SpanImpl> bufferedSpan = new SpanAtomicReference<>();

    // Span links handling
    public static final int MAX_ALLOWED_SPAN_LINKS = 1000;
    private final List<TraceContextImpl> spanLinks = new UniqueSpanLinkArrayList();

    @Nullable
    private OTelSpanKind otelKind = null;

    private final Map<String, Object> otelAttributes = new HashMap<>();

    @Override
    public T requestDiscarding() {
        this.discardRequested = true;
        return thiz();
    }

    @Override
    public boolean isDiscarded() {
        return discardRequested && getTraceContext().isDiscardable();
    }

    private static class ChildDurationTimer implements Recyclable {

        private AtomicInteger activeChildren = new AtomicInteger();
        private AtomicLong start = new AtomicLong();
        private AtomicLong duration = new AtomicLong();

        /**
         * Starts the timer if it has not been started already.
         *
         * @param startTimestamp
         */
        void onChildStart(long startTimestamp) {
            if (activeChildren.incrementAndGet() == 1) {
                start.set(startTimestamp);
            }
        }

        /**
         * Stops the timer and increments the duration if no other direct children are still running
         *
         * @param endTimestamp
         */
        void onChildEnd(long endTimestamp) {
            if (activeChildren.decrementAndGet() == 0) {
                incrementDuration(endTimestamp);
            }
        }

        /**
         * Stops the timer and increments the duration even if there are direct children which are still running
         *
         * @param endTimestamp
         */
        void onSpanEnd(long endTimestamp) {
            if (activeChildren.getAndSet(0) != 0) {
                incrementDuration(endTimestamp);
            }
        }

        private void incrementDuration(long epochMicros) {
            duration.addAndGet(epochMicros - start.get());
        }

        @Override
        public void resetState() {
            activeChildren.set(0);
            start.set(0);
            duration.set(0);
        }

        public long getDuration() {
            return duration.get();
        }
    }

    public AbstractSpanImpl(ElasticApmTracer tracer) {
        super(tracer);
        traceContext = TraceContextImpl.with64BitId(this.tracer);
        boolean selfTimeCollectionEnabled = !WildcardMatcher.isAnyMatch(tracer.getConfig(ReporterConfigurationImpl.class).getDisableMetrics(), "span.self_time");
        boolean breakdownMetricsEnabled = tracer.getConfig(CoreConfigurationImpl.class).isBreakdownMetricsEnabled();
        collectBreakdownMetrics = selfTimeCollectionEnabled && breakdownMetricsEnabled;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    /**
     * How long the transaction took to complete, in µs
     */
    public long getDuration() {
        return endTimestamp.get() - timestamp.get();
    }

    public long getSelfDuration() {
        return getDuration() - childDurations.getDuration();
    }

    public double getDurationMs() {
        return getDuration() / AbstractSpanImpl.MS_IN_MICROS;
    }

    /**
     * Only intended to be used by {@link co.elastic.apm.agent.report.serialize.DslJsonSerializer}
     */
    public CharSequence getNameForSerialization() {
        if (name.length() == 0) {
            return "unnamed";
        } else {
            return name;
        }
    }

    @Override
    @Nullable
    public StringBuilder getAndOverrideName(int namePriority) {
        return getAndOverrideName(namePriority, true);
    }

    @Override
    @Nullable
    public StringBuilder getAndOverrideName(int namePriority, boolean overrideIfSamePriority) {
        boolean shouldOverride = (overrideIfSamePriority) ? namePriority >= this.namePriority : namePriority > this.namePriority;
        if (shouldOverride) {
            this.namePriority = namePriority;
            this.name.setLength(0);
            return name;
        } else {
            return null;
        }
    }

    /**
     * Updates the name of this span to {@code ClassName#methodName}.
     *
     * @param clazz      the class that should be part of this span's name
     * @param methodName the method that should be part of this span's name
     */
    public void updateName(Class<?> clazz, String methodName) {
        StringBuilder spanName = getAndOverrideName(PRIORITY_DEFAULT);
        if (spanName != null) {
            String className = clazz.getName();
            spanName.append(className, className.lastIndexOf('.') + 1, className.length());
            spanName.append("#").append(methodName);
        }
    }

    /**
     * Only intended for testing purposes as this allocates a {@link String}
     *
     * @return name
     */
    public String getNameAsString() {
        return getNameForSerialization().toString();
    }

    @Override
    public T appendToName(CharSequence cs) {
        return appendToName(cs, PRIORITY_DEFAULT);
    }

    @Override
    public T appendToName(CharSequence cs, int priority) {
        return appendToName(cs, priority, 0, cs.length());
    }

    @Override
    public T appendToName(CharSequence cs, int priority, int startIndex, int endIndex) {
        if (priority >= namePriority) {
            this.name.append(cs, startIndex, endIndex);
            this.namePriority = priority;
        }
        return thiz();
    }

    @Override
    public T withName(@Nullable String name) {
        return withName(name, PRIORITY_DEFAULT);
    }

    @Override
    public T withName(@Nullable String name, int priority) {
        return withName(name, priority, true);
    }

    @Override
    public T withName(@Nullable String name, int priority, boolean overrideIfSamePriority) {
        boolean shouldOverride = (overrideIfSamePriority) ? priority >= this.namePriority : priority > this.namePriority;
        if (shouldOverride && name != null && !name.isEmpty()) {
            this.name.setLength(0);
            this.name.append(name);
            this.namePriority = priority;
        }
        return thiz();
    }

    @Override
    public T withType(@Nullable String type) {
        this.type = normalizeEmpty(type);
        return thiz();
    }

    @Override
    public T withSync(boolean sync) {
        this.sync = sync;
        return thiz();
    }



    @Nullable
    protected static String normalizeEmpty(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * Recorded time of the span or transaction in microseconds since epoch
     */
    public long getTimestamp() {
        return timestamp.get();
    }

    @Override
    public TraceContextImpl getTraceContext() {
        return traceContext;
    }

    private boolean canAddSpanLink() {
        if (spanLinks.size() == MAX_ALLOWED_SPAN_LINKS) {
            oneTimeMaxSpanLinksLogger.warn("Span links for {} has reached the allowed maximum ({}). No more spans will be linked.",
                this, MAX_ALLOWED_SPAN_LINKS);
            return false;
        }
        return true;
    }

    /**
     * Adds a span link based on the tracecontext header retrieved from the provided {@code carrier} through the provided {@code
     * headerGetter}.
     *
     * @param headerGetter        the proper header extractor, corresponding the header and carrier types
     * @param carrier             the object from which the tracecontext header is to be retrieved
     * @param <H>                 the tracecontext header type - either binary ({@code byte[]}) or textual ({@code String})
     * @param <C>                 the tracecontext header carrier type, e.g. Kafka record or JMS message
     * @return {@code true} if added, {@code false} otherwise
     */
    public <H, C> boolean addSpanLink(
        HeaderGetter<H, C> headerGetter,
        @Nullable C carrier
    ) {
        if (!canAddSpanLink()) {
            return false;
        }
        boolean added = false;
        try {
            TraceContextImpl childTraceContext = tracer.createSpanLink();
            if (childTraceContext.asChildOf(carrier, headerGetter, false)) {
                added = spanLinks.add(childTraceContext);
            }
            if (!added) {
                tracer.recycle(childTraceContext);
            }
        } catch (Exception e) {
            logger.error(String.format("Failed to add span link to %s from header carrier %s and %s", this, carrier,
                headerGetter.getClass().getName()), e);
        }
        return added;
    }

    /**
     * Adds a span link based on the tracecontext header retrieved from the provided parent.
     *
     * @param childContextCreator the proper tracecontext inference implementation, which retrieves the header
     * @param parent              the object from which the tracecontext header is to be retrieved
     * @param <T>                 the parent type - AbstractSpan, TraceContext or Tracer
     * @return {@code true} if added, {@code false} otherwise
     */
    public <T> boolean addSpanLink(TraceContextImpl.ChildContextCreator<T> childContextCreator, T parent) {
        if (!canAddSpanLink()) {
            return false;
        }
        boolean added = false;
        try {
            TraceContextImpl childTraceContext = tracer.createSpanLink();
            if (childContextCreator.asChildOf(childTraceContext, parent)) {
                added = spanLinks.add(childTraceContext);
            }
            if (!added) {
                tracer.recycle(childTraceContext);
            }
        } catch (Exception e) {
            logger.error(String.format("Failed to add span link to %s from parent %s", this, parent, e));
        }
        return added;
    }

    /**
     * Returns a list of links from this span to other spans in the format of child {@link TraceContextImpl}s, of which parent is the linked
     * span. For each entry in the returned list, the linked span's {@code traceId} can be retrieved through
     * {@link TraceContextImpl#getTraceId()} and the {@code spanId} can be retrieved through {@link TraceContextImpl#getParentId()}.
     *
     * @return a list of child {@link TraceContextImpl}s of linked spans
     */
    public List<TraceContextImpl> getSpanLinks() {
        return spanLinks;
    }

    @Override
    public void resetState() {
        super.resetState();
        finished = true;
        name.setLength(0);
        type = null;
        sync = true;
        timestamp.set(0L);
        endTimestamp.set(0L);
        traceContext.resetState();
        baggage = BaggageImpl.EMPTY;
        childDurations.resetState();
        namePriority = PRIORITY_DEFAULT;
        discardRequested = false;
        isExit = false;
        childIds = null;
        outcome = null;
        userOutcome = null;
        hasCapturedExceptions = false;
        bufferedSpan.reset();
        recycleSpanLinks();
        otelKind = null;
        otelAttributes.clear();
    }

    private void recycleSpanLinks() {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < spanLinks.size(); i++) {
            tracer.recycle(spanLinks.get(i));
        }
        spanLinks.clear();
    }

    @Override
    public SpanImpl createSpan() {
        return createSpan(getBaggage());
    }

    public SpanImpl createSpan(long epochMicros) {
        return createSpan(getBaggage(), epochMicros);
    }

    public SpanImpl createSpan(BaggageImpl newBaggage) {
        return createSpan(newBaggage, traceContext.getClock().getEpochMicros());
    }

    private SpanImpl createSpan(BaggageImpl baggage, long epochMicros) {
        return tracer.startSpan(this, baggage, epochMicros);
    }


    public T asExit() {
        isExit = true;
        return thiz();
    }

    public boolean isExit() {
        return isExit;
    }

    @Nullable
    public String captureExceptionAndGetErrorId(long epochMicros, @Nullable Throwable t) {
        if (t != null) {
            hasCapturedExceptions = true;
            return tracer.captureAndReportException(epochMicros, t, this);
        }
        return null;
    }

    @Override
    public T captureException(@Nullable Throwable t) {
        if (t != null) {
            captureExceptionAndGetErrorId(getTraceContext().getClock().getEpochMicros(), t);
        }
        return thiz();
    }

    public void endExceptionally(@Nullable Throwable t) {
        captureException(t).end();
    }

    @Nullable
    public String captureExceptionAndGetErrorId(@Nullable Throwable t) {
        return captureExceptionAndGetErrorId(getTraceContext().getClock().getEpochMicros(), t);
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

    @Override
    public abstract AbstractContextImpl getContext();

    /**
     * Called after the span has been started and its parent references are set
     */
    protected void onAfterStart() {
        this.finished = false;
        // this final reference is decremented when the span is reported
        // or even after its reported and the last child span is ended
        incrementReferences();

        List<WildcardMatcher> baggageToAttach = tracer.getConfig(CoreConfigurationImpl.class).getBaggageToAttach();
        baggage.storeBaggageInAttributes(this, baggageToAttach);

        if (tracer.getConfig(CoreConfigurationImpl.class).isCaptureThreadOnStart()) {
            Thread currentThread = Thread.currentThread();
            this.addLabel("thread_id", currentThread.getId());
            this.addLabel("thread_name", currentThread.getName());
        }
    }

    @Override
    public void end() {
        end(traceContext.getClock().getEpochMicros());
    }

    public final void end(long epochMicros) {
        if (!finished) {

            long startTime = timestamp.get();
            if(epochMicros < startTime) {
                logger.warn("End called on {} with a timestamp before start! Using start timestamp as end instead.", this);
                epochMicros = startTime;
            }

            this.endTimestamp.set(epochMicros);
            childDurations.onSpanEnd(epochMicros);

            type = normalizeType(type);

            beforeEnd(epochMicros);
            this.finished = true;
            SpanImpl buffered = bufferedSpan.incrementReferencesAndGet();
            if (buffered != null) {
                try {
                    if (bufferedSpan.compareAndSet(buffered, null)) {
                        this.tracer.endSpan(buffered);
                        logger.trace("span compression buffer was set to null and {} was ended", buffered);
                    }
                } finally {
                    buffered.decrementReferences();
                }
            }
            afterEnd();
        } else {
            if (oneTimeDuplicatedEndLogger.isWarnEnabled()) {
                oneTimeDuplicatedEndLogger.warn("End has already been called: " + this, new Throwable());
            } else {
                logger.warn("End has already been called: {}", this);
                logger.debug("Consecutive AbstractSpan#end() invocation stack trace: ", new Throwable());
            }
            assert false;
        }
    }

    /**
     * @return true if outcome has NOT been set, either by user or through instrumentation
     */
    protected boolean outcomeNotSet() {
        return userOutcome == null && outcome == null;
    }

    /**
     * @return true if an exception has been captured
     */
    protected boolean hasCapturedExceptions() {
        return hasCapturedExceptions;
    }

    protected abstract void beforeEnd(long epochMicros);

    protected abstract void afterEnd();

    public boolean isChildOf(AbstractSpanImpl<?> parent) {
        return traceContext.isChildOf(parent.traceContext) || parent.hasChildId(traceContext.getId());
    }

    private boolean hasChildId(IdImpl spanId) {
        if (childIds != null) {
            return childIds.contains(spanId.readLong(0));
        }
        return false;
    }

    @Override
    public BaggageImpl getBaggage() {
        return baggage;
    }

    /**
     * Returns this, if this AbstractSpan is a {@link co.elastic.apm.agent.tracer.Transaction}.
     * Otherwise returns the parent transaction of this span.
     *
     * @return the transaction.
     */
    public abstract TransactionImpl getParentTransaction();

    /**
     * Set start timestamp
     *
     * @param epochMicros start timestamp in micro-seconds since epoch
     */
    public void setStartTimestamp(long epochMicros) {
        timestamp.set(epochMicros);
    }

    /**
     * Set start timestamp from context current clock
     */
    public void setStartTimestampNow() {
        timestamp.set(getTraceContext().getClock().getEpochMicros());
    }

    void onChildStart(long epochMicros) {
        if (collectBreakdownMetrics) {
            childDurations.onChildStart(epochMicros);
        }
    }

    void onChildEnd(long epochMicros) {
        if (collectBreakdownMetrics) {
            childDurations.onChildEnd(epochMicros);
        }
    }

    @Override
    public void setNonDiscardable() {
        getTraceContext().setNonDiscardable();
    }

    /**
     * Returns whether it's possible to discard this span.
     *
     * @return {@code true}, if it's safe to discard the span, {@code false} otherwise.
     */
    public boolean isDiscardable() {
        return getTraceContext().isDiscardable();
    }

    @Override
    public boolean isSampled() {
        return getTraceContext().isSampled();
    }

    public T withChildIds(@Nullable LongList childIds) {
        this.childIds = childIds;
        return thiz();
    }

    @Nullable
    public LongList getChildIds() {
        return childIds;
    }

    protected abstract T thiz();

    @Override
    public Outcome getOutcome() {
        if (userOutcome != null) {
            return userOutcome;
        }
        return outcome != null ? outcome : Outcome.UNKNOWN;
    }

    @Override
    public T withOutcome(Outcome outcome) {
        this.outcome = outcome;
        return thiz();
    }

    /**
     * Sets user outcome, which has priority over outcome set through {@link #withOutcome(Outcome)}
     *
     * @param outcome user outcome
     * @return this
     */
    public T withUserOutcome(Outcome outcome) {
        this.userOutcome = outcome;
        return thiz();
    }

    @Override
    public AbstractSpanImpl<?> getSpan() {
        return this;
    }

    public T withOtelKind(OTelSpanKind kind) {
        this.otelKind = kind;
        return thiz();
    }

    @Nullable
    public OTelSpanKind getOtelKind() {
        return otelKind;
    }

    public Map<String, Object> getOtelAttributes() {
        return otelAttributes;
    }

    @Override
    public T withOtelAttribute(String key, @Nullable Object value) {
        if (value != null) {
            otelAttributes.put(key, value);
        }
        return thiz();
    }

    @Override
    @Nullable
    public String getType() {
        return type;
    }

    public boolean isSync() {
        return sync;
    }

    private String normalizeType(@Nullable String type) {
        if (type == null || type.isEmpty()) {
            return "custom";
        }
        return type;
    }

    @Override
    public <T, C> boolean addLink(HeaderGetter<T, C> headerGetter, @Nullable C carrier) {
        return addSpanLink(headerGetter, carrier);
    }
}
