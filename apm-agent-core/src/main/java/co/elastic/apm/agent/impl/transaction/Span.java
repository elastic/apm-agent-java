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
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.context.Message;
import co.elastic.apm.agent.impl.context.SpanContext;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Span extends AbstractSpan<Span> implements Recyclable {

    private static final Logger logger = LoggerFactory.getLogger(Span.class);
    public static final long MAX_LOG_INTERVAL_MICRO_SECS = TimeUnit.MINUTES.toMicros(5);
    private static long lastSpanMaxWarningTimestamp;

    /**
     * General type describing this span (eg: 'db', 'ext', 'template', etc)
     * (Required)
     */
    @Nullable
    private String type;

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

    /**
     * Keywords of specific relevance in the span's domain (eg: 'db', 'template', 'ext', etc)
     */
    public Span withType(@Nullable String type) {
        this.type = normalizeEmpty(type);
        return this;
    }

    /**
     * Sets the span's subtype, related to the  (eg: 'mysql', 'postgresql', 'jsf' etc)
     */
    public Span withSubtype(@Nullable String subtype) {
        this.subtype = normalizeEmpty(subtype);
        return this;
    }

    /**
     * Action related to this span (eg: 'query', 'render' etc)
     */
    public Span withAction(@Nullable String action) {
        this.action = normalizeEmpty(action);
        return this;
    }

    @Nullable
    private static String normalizeEmpty(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
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

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public String getSubtype() {
        return subtype;
    }

    @Nullable
    public String getAction() {
        return action;
    }

    @Override
    public void beforeEnd(long epochMicros) {
        if (logger.isDebugEnabled()) {
            logger.debug("endSpan {}", this);
            if (logger.isTraceEnabled()) {
                logger.trace("ending span at", new RuntimeException("this exception is just used to record where the span has been ended from"));
            }
        }
        if (type == null) {
            type = "custom";
        }

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
        // https://github.com/elastic/apm/blob/master/specs/agents/tracing-spans-destination.md#contextdestinationserviceresource
        Destination.Service service = getContext().getDestination().getService();
        StringBuilder serviceResource = service.getResource();
        if (isExit() && serviceResource.length() == 0 && !service.isResourceSetByUser()) {
            String resourceType = (subtype != null) ? subtype : type;
            Db db = context.getDb();
            Message message = context.getMessage();
            Url internalUrl = context.getHttp().getInternalUrl();
            if (db.hasContent()) {
                serviceResource.append(resourceType);
                if (db.getInstance() != null) {
                    serviceResource.append('/').append(db.getInstance());
                }
            } else if (message.hasContent()) {
                serviceResource.append(resourceType);
                if (message.getQueueName() != null) {
                    serviceResource.append('/').append(message.getQueueName());
                }
            } else if (internalUrl.hasContent()) {
                serviceResource.append(internalUrl.getHostname());
                if (internalUrl.getPort() > 0) {
                    serviceResource.append(':').append(internalUrl.getPort());
                }
            } else {
                serviceResource.append(resourceType);
            }
        }

        if (transaction != null) {
            transaction.incrementTimer(type, subtype, getSelfDuration());
        }
        if (parent != null) {
            parent.onChildEnd(epochMicros);
            parent.decrementReferences();
        }
    }

    @Override
    protected void afterEnd() {
        this.tracer.endSpan(this);
    }

    @Override
    public void resetState() {
        super.resetState();
        context.resetState();
        stacktrace = null;
        type = null;
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
