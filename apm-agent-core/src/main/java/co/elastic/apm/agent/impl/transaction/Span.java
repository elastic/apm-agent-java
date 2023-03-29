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
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Db;
import co.elastic.apm.agent.impl.context.Message;
import co.elastic.apm.agent.impl.context.ServiceTarget;
import co.elastic.apm.agent.impl.context.SpanContext;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.util.CharSequenceUtils;
import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Span extends AbstractSpan<Span> implements Recyclable, co.elastic.apm.agent.tracer.Span<Span> {

    private static final Logger logger = LoggerFactory.getLogger(Span.class);
    public static final long MAX_LOG_INTERVAL_MICRO_SECS = TimeUnit.MINUTES.toMicros(5);
    private static long lastSpanMaxWarningTimestamp;

    /**
     * A subtype describing this span (eg 'mysql', 'elasticsearch', 'jsf' etc)
     * (Optional)
     */
    @Nullable
    private String subtype;

    /**
     * An action describing this span (eg 'query', 'execute', 'render' etc)
     * (Optional)
     */
    @Nullable
    private String action;

    /**
     * Any other arbitrary data captured by the agent, optionally provided by the user
     */
    private final SpanContext context = new SpanContext();
    private final Composite composite = new Composite();
    @Nullable
    private Throwable stacktrace;
    @Nullable
    private AbstractSpan<?> parent;
    @Nullable
    private Transaction transaction;
    @Nullable
    private List<StackFrame> stackFrames;

    /**
     * If a span is non-discardable, all the spans leading up to it are non-discardable as well
     */
    public void setNonDiscardable() {
        if (isDiscardable()) {
            getTraceContext().setNonDiscardable();
            if (parent != null) {
                parent.setNonDiscardable();
            }
        }
    }

    public Span(ElasticApmTracer tracer) {
        super(tracer);
    }

    public <T> Span start(TraceContext.ChildContextCreator<T> childContextCreator, T parentContext, long epochMicros) {
        childContextCreator.asChildOf(traceContext, parentContext);
        if (parentContext instanceof Transaction) {
            this.transaction = (Transaction) parentContext;
            this.parent = this.transaction;
        } else if (parentContext instanceof Span) {
            final Span parentSpan = (Span) parentContext;
            this.parent = parentSpan;
            this.transaction = parentSpan.transaction;
        }
        return start(epochMicros);
    }

    private Span start(long epochMicros) {
        if (transaction != null) {
            SpanCount spanCount = transaction.getSpanCount();
            if (transaction.isSpanLimitReached()) {
                if (epochMicros - lastSpanMaxWarningTimestamp > MAX_LOG_INTERVAL_MICRO_SECS) {
                    lastSpanMaxWarningTimestamp = epochMicros;
                    logger.warn("Max spans ({}) for transaction {} has been reached. For this transaction and possibly others, further spans will be dropped. See config param 'transaction_max_spans'.",
                        tracer.getConfig(CoreConfiguration.class).getTransactionMaxSpans(), transaction);
                }
                logger.debug("Span exceeds transaction_max_spans {}", this);
                traceContext.setRecorded(false);
                spanCount.getDropped().incrementAndGet();
            }
            spanCount.getTotal().incrementAndGet();
        }
        if (epochMicros >= 0) {
            setStartTimestamp(epochMicros);
        } else {
            setStartTimestampNow();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("startSpan {}", this);
            if (logger.isTraceEnabled()) {
                logger.trace("starting span at",
                    new RuntimeException("this exception is just used to record where the span has been started from"));
            }
        }
        onAfterStart();
        return this;
    }

    @Override
    protected void onAfterStart() {
        super.onAfterStart();
        if (parent != null) {
            this.parent.incrementReferences();
            this.parent.onChildStart(getTimestamp());
        }
    }

    /**
     * Any other arbitrary data captured by the agent, optionally provided by the user
     */
    @Override
    public SpanContext getContext() {
        return context;
    }

    public boolean isComposite() {
        return composite.getCount() > 0;
    }

    public Composite getComposite() {
        return composite;
    }

    @Override
    public Span withSubtype(@Nullable String subtype) {
        this.subtype = normalizeEmpty(subtype);
        return this;
    }

    @Override
    public Span withAction(@Nullable String action) {
        this.action = normalizeEmpty(action);
        return this;
    }


    /**
     * Sets span.type, span.subtype and span.action. If no subtype and action are provided, assumes the legacy usage of hierarchical
     * typing system and attempts to split the type by dots to discover subtype and action.
     * TODO: remove in 2.0 - no need for that once we decide to drop support for old agent usages
     */
    @Deprecated
    public void setType(@Nullable String type, @Nullable String subtype, @Nullable String action) {
        if (type != null && (subtype == null || subtype.isEmpty()) && (action == null || action.isEmpty())) {
            // hierarchical typing - pre 1.4; we need to split
            String temp = type;
            int indexOfFirstDot = temp.indexOf(".");
            if (indexOfFirstDot > 0) {
                type = temp.substring(0, indexOfFirstDot);
                int indexOfSecondDot = temp.indexOf(".", indexOfFirstDot + 1);
                if (indexOfSecondDot > 0) {
                    subtype = temp.substring(indexOfFirstDot + 1, indexOfSecondDot);
                    if (indexOfSecondDot + 1 < temp.length()) {
                        action = temp.substring(indexOfSecondDot + 1);
                    }
                }
            }
        }
        withType(type);
        withSubtype(subtype);
        withAction(action);
    }

    @Nullable
    public Throwable getStacktrace() {
        return stacktrace;
    }

    @Override
    @Nullable
    public String getSubtype() {
        return subtype;
    }

    @Override
    @Nullable
    public String getAction() {
        return action;
    }

    @Override
    public void beforeEnd(long epochMicros) {
        // set outcome when not explicitly set by user nor instrumentation
        if (outcomeNotSet()) {
            Outcome outcome;
            if (context.getHttp().hasContent()) {
                // HTTP client spans
                outcome = ResultUtil.getOutcomeByHttpClientStatus(context.getHttp().getStatusCode());
            } else {
                // span types & sub-types for which we consider getting an exception as a failure
                outcome = hasCapturedExceptions() ? Outcome.FAILURE : Outcome.SUCCESS;
            }
            withOutcome(outcome);
        }

        // auto-infer context.destination.service.resource as per spec:
        // https://github.com/elastic/apm/blob/main/specs/agents/tracing-spans-destination.md#contextdestinationserviceresource
        ServiceTarget serviceTarget = getContext().getServiceTarget();
        if (isExit() && !serviceTarget.hasContent() && !serviceTarget.isSetByUser()) {
            Db db = context.getDb();
            Message message = context.getMessage();
            Url httpUrl = context.getHttp().getInternalUrl();
            String targetServiceType = (subtype != null) ? subtype : type;
            if (db.hasContent()) {
                serviceTarget.withType(targetServiceType).withName(db.getInstance());
            } else if (message.hasContent()) {
                serviceTarget.withType(targetServiceType).withName(message.getQueueName());
            } else if (httpUrl.hasContent()) {

                // direct modification of destination resource to ensure compatibility
                serviceTarget.withType("http")
                    .withHostPortName(httpUrl.getHostname(), httpUrl.getPort())
                    .withNameOnlyDestinationResource();
            } else {
                serviceTarget.withType(targetServiceType);
            }
        }

        if (transaction != null) {
            transaction.incrementTimer(type, subtype, getSelfDuration());
        }
        if (parent != null) {
            parent.onChildEnd(epochMicros);
        }
    }

    @Override
    protected void afterEnd() {
        // Why do we increment references of this span here?
        // The only thing preventing the "this"-span from being recycled is the initial reference increment in onAfterStart()
        // There are multiple ways in afterEnd() on how this reference may be decremented and therefore potentially causing recycling:
        //  - we call tracer.endSpan() for this span and the span is dropped / not reported for some reason
        //  - we call tracer.endSpan() for this span and the span is reported and released afterwards (=> recycled on the reporter thread!)
        //  - we successfully set parent.bufferedSpan to "this".
        //     - a span on a different thread with the same parent can now call tracer.endSpan() for parent.bufferedSpan (=this span)
        //     - the parent span is ended on a different thread and calls tracer.endSpan() for parent.bufferedSpan (=this span)
        // By incrementing the reference count here, we guarantee that the "this" span is only recycled AFTER we decrement the reference count again
        this.incrementReferences();
        try {
            if (transaction != null && transaction.isSpanCompressionEnabled() && parent != null) {
                Span parentBuffered = parent.bufferedSpan.incrementReferencesAndGet();
                try {
                    //per the reference, if it is not compression-eligible or if its parent has already ended, it is reported immediately
                    if (parent.isFinished() || !isCompressionEligible()) {
                        if (parentBuffered != null) {
                            if (parent.bufferedSpan.compareAndSet(parentBuffered, null)) {
                                this.tracer.endSpan(parentBuffered);
                            }
                        }
                        this.tracer.endSpan(this);
                        return;
                    }
                    //since it wasn't reported, this span gets buffered
                    if (parentBuffered == null) {
                        if (!parent.bufferedSpan.compareAndSet(null, this)) {
                            // the failed update would ideally lead to a compression attempt with the new buffer,
                            // but we're dropping the compression attempt to keep things simple and avoid looping so this stays wait-free
                            // this doesn't exactly diverge from the spec, but it can lead to non-optimal compression under high load
                            this.tracer.endSpan(this);
                        }
                        return;
                    }
                    //still trying to buffer this span
                    if (!parentBuffered.tryToCompress(this)) {
                        // we couldn't compress so replace the buffer with this
                        if (parent.bufferedSpan.compareAndSet(parentBuffered, this)) {
                            this.tracer.endSpan(parentBuffered);
                        } else {
                            // the failed update would ideally lead to a compression attempt with the new buffer,
                            // but we're dropping the compression attempt to keep things simple and avoid looping so this stays wait-free
                            // this doesn't exactly diverge from the spec, but it can lead to non-optimal compression under high load
                            this.tracer.endSpan(this);
                        }
                    } else {
                        if (isSampled() && transaction != null) {
                            transaction.getSpanCount().getDropped().incrementAndGet();
                        }
                        //drop the span by removing the reference allocated in onAfterStart() because it has been compressed
                        decrementReferences();
                    }
                } finally {
                    if (parentBuffered != null) {
                        parentBuffered.decrementReferences();
                    }
                }
            } else {
                this.tracer.endSpan(this);
            }
        } finally {
            if (parent != null) {
                //this needs to happen before this.decrementReferences(), because otherwise "this" might have been recycled already
                parent.decrementReferences();
            }
            this.decrementReferences();
        }
    }

    private boolean isCompressionEligible() {
        return isExit() && isDiscardable() && (outcomeNotSet() || getOutcome() == Outcome.SUCCESS);
    }

    private boolean tryToCompress(Span sibling) {
        boolean canBeCompressed = isComposite() ? tryToCompressComposite(sibling) : tryToCompressRegular(sibling);
        if (!canBeCompressed) {
            return false;
        }

        do {
            long currentTimestamp = timestamp.get();
            if (currentTimestamp <= sibling.timestamp.get()) {
                break;
            }
            if (timestamp.compareAndSet(currentTimestamp, sibling.timestamp.get())) {
                break;
            }
        } while (true);

        do {
            long currentEndTimestamp = endTimestamp.get();
            if (sibling.endTimestamp.get() <= currentEndTimestamp) {
                break;
            }
            if (endTimestamp.compareAndSet(currentEndTimestamp, sibling.endTimestamp.get())) {
                break;
            }
        } while (true);

        composite.increaseCount();
        composite.increaseSum(sibling.getDuration());

        return true;
    }

    private boolean tryToCompressRegular(Span sibling) {
        if (!isSameKind(sibling)) {
            return false;
        }

        long currentDuration = getDuration();
        if (isComposite()) {
            return tryToCompressComposite(sibling);
        }

        if (CharSequenceUtils.equals(name, sibling.name)) {
            long maxExactMatchDuration = transaction.getSpanCompressionExactMatchMaxDurationUs();
            if (currentDuration <= maxExactMatchDuration && sibling.getDuration() <= maxExactMatchDuration) {
                if (!composite.init(currentDuration, "exact_match")) {
                    return tryToCompressComposite(sibling);
                }
                return true;
            }
            return false;
        }

        long maxSameKindDuration = transaction.getSpanCompressionSameKindMaxDurationUs();
        if (currentDuration <= maxSameKindDuration && sibling.getDuration() <= maxSameKindDuration) {
            if (!composite.init(currentDuration, "same_kind")) {
                return tryToCompressComposite(sibling);
            }
            setCompressedSpanName();
            return true;
        }

        return false;
    }

    private void setCompressedSpanName() {
        name.setLength(0);

        ServiceTarget serviceTarget = context.getServiceTarget();
        String serviceType = serviceTarget.getType();
        CharSequence serviceName = serviceTarget.getName();

        name.append("Calls to ");
        if (serviceType == null && serviceName == null) {
            name.append("unknown");
        } else {
            boolean hasType = serviceType != null;
            if (hasType) {
                name.append(serviceType);
            }
            if (serviceName != null) {
                if (hasType) {
                    name.append('/');
                }
                name.append(serviceName);
            }
        }
    }

    private boolean tryToCompressComposite(Span sibling) {
        String compressionStrategy = composite.getCompressionStrategy();
        if (compressionStrategy == null) {
            //lose the compression rather than retry, so that the application proceeds with minimum delay
            return false;
        }

        switch (compressionStrategy) {
            case "exact_match":
                long maxExactMatchDuration = transaction.getSpanCompressionExactMatchMaxDurationUs();
                return isSameKind(sibling) && CharSequenceUtils.equals(name, sibling.name) && sibling.getDuration() <= maxExactMatchDuration;

            case "same_kind":
                long maxSameKindDuration = transaction.getSpanCompressionSameKindMaxDurationUs();
                return isSameKind(sibling) && sibling.getDuration() <= maxSameKindDuration;
            default:
        }

        return false;
    }

    private boolean isSameKind(Span other) {
        ServiceTarget serviceTarget = context.getServiceTarget();
        ServiceTarget otherServiceTarget = other.context.getServiceTarget();
        return Objects.equals(type, other.type)
            && Objects.equals(subtype, other.subtype)
            && Objects.equals(serviceTarget.getType(), otherServiceTarget.getType())
            && CharSequenceUtils.equals(serviceTarget.getName(), otherServiceTarget.getName());
    }

    @Override
    public void resetState() {
        super.resetState();
        context.resetState();
        composite.resetState();
        stacktrace = null;
        subtype = null;
        action = null;
        parent = null;
        transaction = null;
        // recycling this array list by clear()-ing it doesn't seem worth it
        // it's used in the context of profiling-inferred spans which entails allocations anyways
        // when trying to recycle this list by clearing it, we increase the static memory overhead of the agent
        // because all spans in the pool contain that list even if they are not used as inferred spans
        stackFrames = null;
    }

    @Override
    public String toString() {
        return String.format("'%s' %s (%s)", name, traceContext, Integer.toHexString(System.identityHashCode(this)));
    }

    public Span withStacktrace(Throwable stacktrace) {
        this.stacktrace = stacktrace;
        return this;
    }

    @Override
    public void incrementReferences() {
        if (transaction != null) {
            transaction.incrementReferences();
        }
        super.incrementReferences();
    }

    @Override
    public void decrementReferences() {
        if (transaction != null) {
            transaction.decrementReferences();
        }
        super.decrementReferences();
    }

    @Override
    protected void recycle() {
        tracer.recycle(this);
    }

    @Override
    protected Span thiz() {
        return this;
    }

    public void setStackTrace(List<StackFrame> stackTrace) {
        this.stackFrames = stackTrace;
    }

    @Nullable
    public List<StackFrame> getStackFrames() {
        return stackFrames;
    }

    @Nullable
    @Override
    public Transaction getTransaction() {
        return transaction;
    }

    @Nullable
    public AbstractSpan<?> getParent() {
        return parent;
    }
}
