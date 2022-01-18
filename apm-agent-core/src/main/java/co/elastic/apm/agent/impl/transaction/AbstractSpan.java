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

import co.elastic.apm.agent.collections.LongList;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.context.AbstractContext;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractSpan<T extends AbstractSpan<T>> implements Recyclable {
    public static final int PRIO_USER_SUPPLIED = 1000;
    public static final int PRIO_HIGH_LEVEL_FRAMEWORK = 100;
    public static final int PRIO_METHOD_SIGNATURE = 100;
    public static final int PRIO_LOW_LEVEL_FRAMEWORK = 10;
    public static final int PRIO_DEFAULT = 0;
    private static final Logger logger = LoggerFactory.getLogger(AbstractSpan.class);
    protected static final double MS_IN_MICROS = TimeUnit.MILLISECONDS.toMicros(1);
    protected final TraceContext traceContext;

    /**
     * Generic designation of a transaction in the scope of a single service (eg: 'GET /users/:id')
     */
    protected final StringBuilder name = new StringBuilder();
    protected final boolean collectBreakdownMetrics;
    protected final ElasticApmTracer tracer;
    private long timestamp;

    // in microseconds
    protected long duration;
    private ChildDurationTimer childDurations = new ChildDurationTimer();
    protected AtomicInteger references = new AtomicInteger();
    protected volatile boolean finished = true;
    private int namePriority = PRIO_DEFAULT;
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
     * The effect is that a regular span cannot have a {@link TraceContext#parentId} pointing to an inferred span.
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

    public int getReferenceCount() {
        return references.get();
    }

    /**
     * Requests this span to be discarded, even if it's sampled.
     * <p>
     * Whether the span can actually be discarded is determined by {@link #isDiscarded()}
     * </p>
     */
    public T requestDiscarding() {
        this.discardRequested = true;
        return (T) this;
    }

    /**
     * Determines whether to discard the span.
     * Only spans that return {@code false} are reported.
     * <p>
     * A span is discarded if it {@linkplain #isDiscardable() is discardable} and {@linkplain #requestDiscarding() requested to be discarded}.
     * </p>
     *
     * @return {@code true}, if the span should be discarded, {@code false} otherwise.
     */
    public boolean isDiscarded() {
        return discardRequested && getTraceContext().isDiscardable();
    }

    @Nullable
    public abstract Transaction getTransaction();

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

    public AbstractSpan(ElasticApmTracer tracer) {
        this.tracer = tracer;
        traceContext = TraceContext.with64BitId(this.tracer);
        boolean selfTimeCollectionEnabled = !WildcardMatcher.isAnyMatch(tracer.getConfig(ReporterConfiguration.class).getDisableMetrics(), "span.self_time");
        boolean breakdownMetricsEnabled = tracer.getConfig(CoreConfiguration.class).isBreakdownMetricsEnabled();
        collectBreakdownMetrics = selfTimeCollectionEnabled && breakdownMetricsEnabled;
    }

    public boolean isReferenced() {
        return references.get() > 0;
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * How long the transaction took to complete, in µs
     */
    public long getDuration() {
        return duration;
    }

    public long getSelfDuration() {
        return duration - childDurations.getDuration();
    }

    public double getDurationMs() {
        return duration / AbstractSpan.MS_IN_MICROS;
    }

    /**
     * Only intended to be used by {@link co.elastic.apm.agent.report.serialize.DslJsonSerializer}
     */
    public StringBuilder getNameForSerialization() {
        return name;
    }

    /**
     * Resets and returns the name {@link StringBuilder} if the provided priority is {@code >=} {@link #namePriority} one.
     * Otherwise, returns {@code null}
     *
     * @param namePriority the priority for the name. See also the {@code AbstractSpan#PRIO_*} constants.
     * @return the name {@link StringBuilder} if the provided priority is {@code >=} {@link #namePriority}, {@code null} otherwise.
     */
    @Nullable
    public StringBuilder getAndOverrideName(int namePriority) {
        return getAndOverrideName(namePriority, true);
    }

    /**
     * Resets and returns the name {@link StringBuilder} if one of the following applies:
     * <ul>
     *      <li>the provided priority is {@code >} {@link #namePriority}</li>
     *      <li>the provided priority is {@code ==} {@link #namePriority} AND {@code overrideIfSamePriority} is {@code true}</li>
     * </ul>
     * Otherwise, returns {@code null}
     *
     * @param namePriority           the priority for the name. See also the {@code AbstractSpan#PRIO_*} constants.
     * @param overrideIfSamePriority specifies whether the existing name should be overridden if {@code namePriority} equals the priority used to set the current name
     * @return the name {@link StringBuilder} if the provided priority is sufficient for overriding, {@code null} otherwise.
     */
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
        StringBuilder spanName = getAndOverrideName(PRIO_DEFAULT);
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
        return name.toString();
    }

    /**
     * Appends a string to the name.
     * <p>
     * This method helps to avoid the memory allocations of string concatenations
     * as the underlying {@link StringBuilder} instance will be reused.
     * </p>
     *
     * @param cs the char sequence to append to the name
     * @return {@code this}, for chaining
     */
    public T appendToName(CharSequence cs) {
        return appendToName(cs, PRIO_DEFAULT);
    }

    public T appendToName(CharSequence cs, int priority) {
        if (priority >= namePriority) {
            this.name.append(cs);
            this.namePriority = priority;
        }
        return thiz();
    }

    public T withName(@Nullable String name) {
        return withName(name, PRIO_DEFAULT);
    }

    public T withName(@Nullable String name, int priority) {
        return withName(name, priority, true);
    }

    public T withName(@Nullable String name, int priority, boolean overrideIfSamePriority) {
        boolean shouldOverride = (overrideIfSamePriority) ? priority >= this.namePriority : priority > this.namePriority;
        if (shouldOverride && name != null && !name.isEmpty()) {
            this.name.setLength(0);
            this.name.append(name);
            this.namePriority = priority;
        }
        return thiz();
    }

    /**
     * Recorded time of the span or transaction in microseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    @Override
    public void resetState() {
        finished = true;
        name.setLength(0);
        timestamp = 0;
        duration = 0;
        traceContext.resetState();
        childDurations.resetState();
        references.set(0);
        namePriority = PRIO_DEFAULT;
        discardRequested = false;
        isExit = false;
        childIds = null;
        outcome = null;
        userOutcome = null;
        hasCapturedExceptions = false;
    }

    public Span createSpan() {
        return createSpan(traceContext.getClock().getEpochMicros());
    }

    public Span createSpan(long epochMicros) {
        return tracer.startSpan(this, epochMicros);
    }

    /**
     * Creates a child Span representing a remote call event, unless this TraceContextHolder already represents an exit event.
     * If current TraceContextHolder is representing an Exit- returns null
     *
     * @return an Exit span if this TraceContextHolder is not an exit span, null otherwise
     */
    @Nullable
    public Span createExitSpan() {
        if (isExit()) {
            return null;
        }
        return createSpan().asExit();
    }


    public T asExit() {
        isExit = true;
        return (T) this;
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

    public T captureException(@Nullable Throwable t) {
        if (t != null) {
            captureExceptionAndGetErrorId(getTraceContext().getClock().getEpochMicros(), t);
        }
        return (T) this;
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

    public abstract AbstractContext getContext();

    /**
     * Called after the span has been started and its parent references are set
     */
    protected void onAfterStart() {
        this.finished = false;
        // this final reference is decremented when the span is reported
        // or even after its reported and the last child span is ended
        incrementReferences();
    }

    public void end() {
        end(traceContext.getClock().getEpochMicros());
    }

    public final void end(long epochMicros) {
        if (!finished) {
            this.duration = (epochMicros - timestamp);
            if (name.length() == 0) {
                name.append("unnamed");
            }
            childDurations.onSpanEnd(epochMicros);
            beforeEnd(epochMicros);
            this.finished = true;
            afterEnd();
        } else {
            logger.warn("End has already been called: {}", this);
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

    public boolean isChildOf(AbstractSpan<?> parent) {
        return traceContext.isChildOf(parent.traceContext) || parent.hasChildId(traceContext.getId());
    }

    private boolean hasChildId(Id spanId) {
        if (childIds != null) {
            return childIds.contains(spanId.readLong(0));
        }
        return false;
    }

    public T activate() {
        tracer.activate(this);
        return (T) this;
    }

    public T deactivate() {
        tracer.deactivate(this);
        return (T) this;
    }

    public Scope activateInScope() {
        // already in scope
        if (tracer.getActive() == this) {
            return Scope.NoopScope.INSTANCE;
        }
        activate();
        return new Scope() {
            @Override
            public void close() {
                deactivate();
            }
        };
    }

    /**
     * Set start timestamp
     *
     * @param epochMicros start timestamp in micro-seconds since epoch
     */
    public void setStartTimestamp(long epochMicros) {
        timestamp = epochMicros;
    }

    /**
     * Set start timestamp from context current clock
     */
    public void setStartTimestampNow() {
        timestamp = getTraceContext().getClock().getEpochMicros();
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

    public void incrementReferences() {
        int referenceCount = references.incrementAndGet();
        if (logger.isDebugEnabled()) {
            logger.debug("increment references to {} ({})", this, referenceCount);
            if (logger.isTraceEnabled()) {
                logger.trace("incrementing references at",
                    new RuntimeException("This is an expected exception. Is just used to record where the reference count has been incremented."));
            }
        }
    }

    public void decrementReferences() {
        int referenceCount = references.decrementAndGet();
        if (logger.isDebugEnabled()) {
            logger.debug("decrement references to {} ({})", this, referenceCount);
            if (logger.isTraceEnabled()) {
                logger.trace("decrementing references at",
                    new RuntimeException("This is an expected exception. Is just used to record where the reference count has been decremented."));
            }
        }
        if (referenceCount == 0) {
            recycle();
        }
    }

    protected abstract void recycle();

    /**
     * Sets Trace context text headers, using this context as parent, on the provided carrier using the provided setter
     *
     * @param carrier      the text headers carrier
     * @param headerSetter a setter implementing the actual addition of headers to the headers carrier
     * @param <C>          the header carrier type, for example - an HTTP request
     */
    public <C> void propagateTraceContext(C carrier, TextHeaderSetter<C> headerSetter) {
        // the context of this span is propagated downstream so we can't discard it even if it's faster than span_min_duration
        setNonDiscardable();
        getTraceContext().propagateTraceContext(carrier, headerSetter);
    }

    /**
     * Sets Trace context binary headers, using this context as parent, on the provided carrier using the provided setter
     *
     * @param carrier      the binary headers carrier
     * @param headerSetter a setter implementing the actual addition of headers to the headers carrier
     * @param <C>          the header carrier type, for example - a Kafka record
     * @return true if Trace Context headers were set; false otherwise
     */
    public <C> boolean propagateTraceContext(C carrier, BinaryHeaderSetter<C> headerSetter) {
        // the context of this span is propagated downstream so we can't discard it even if it's faster than span_min_duration
        setNonDiscardable();
        return getTraceContext().propagateTraceContext(carrier, headerSetter);
    }

    /**
     * Sets this context as non-discardable,
     * meaning that {@link AbstractSpan#isDiscarded()} will return {@code false},
     * even if {@link AbstractSpan#requestDiscarding()} has been called.
     */
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

    /**
     * @return user outcome if set, otherwise outcome value
     */
    public Outcome getOutcome() {
        if (userOutcome != null) {
            return userOutcome;
        }
        return outcome != null ? outcome : Outcome.UNKNOWN;
    }

    /**
     * Sets outcome
     *
     * @param outcome outcome
     * @return this
     */
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

}
