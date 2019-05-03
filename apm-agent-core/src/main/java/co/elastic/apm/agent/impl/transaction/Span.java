/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.SpanContext;
import co.elastic.apm.agent.objectpool.Recyclable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class Span extends AbstractSpan<Span> implements Recyclable {

    private static final Logger logger = LoggerFactory.getLogger(Span.class);

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

    public Span(ElasticApmTracer tracer) {
        super(tracer);
    }

    public <T> Span start(TraceContext.ChildContextCreator<T> childContextCreator, T parentContext, long epochMicros, boolean dropped) {
        childContextCreator.asChildOf(traceContext, parentContext);
        if (parentContext instanceof Transaction) {
            this.transaction = (Transaction) parentContext;
            this.parent = this.transaction;
        } else if (parentContext instanceof Span) {
            final Span parentSpan = (Span) parentContext;
            this.parent = parentSpan;
            this.transaction = parentSpan.transaction;
        }
        if (dropped) {
            traceContext.setRecorded(false);
        }
        if (epochMicros >= 0) {
            setStartTimestamp(epochMicros);
        } else {
            setStartTimestamp(getTraceContext().getClock().getEpochMicros());
        }
        if (logger.isDebugEnabled()) {
            logger.debug("startSpan {} {", this);
            if (logger.isTraceEnabled()) {
                logger.trace("starting span at",
                    new RuntimeException("this exception is just used to record where the span has been started from"));
            }
        }
        onAfterStart();
        return this;
    }

    /**
     * Any other arbitrary data captured by the agent, optionally provided by the user
     */
    @Override
    public SpanContext getContext() {
        return context;
    }

    public Span withName(@Nullable String name) {
        setName(name);
        return this;
    }

    /**
     * Keywords of specific relevance in the span's domain (eg: 'db', 'template', 'ext', etc)
     */
    public Span withType(@Nullable String type) {
        this.type = type;
        return this;
    }

    /**
     * Sets the span's subtype, related to the  (eg: 'mysql', 'postgresql', 'jsf' etc)
     */
    public Span withSubtype(@Nullable String subtype) {
        this.subtype = subtype;
        return this;
    }

    /**
     * Action related to this span (eg: 'query', 'render' etc)
     */
    public Span withAction(@Nullable String action) {
        this.action = action;
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
        this.type = type;
        this.subtype = subtype;
        this.action = action;
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
    public void doEnd(long epochMicros) {
        if (logger.isDebugEnabled()) {
            logger.debug("} endSpan {}", this);
            if (logger.isTraceEnabled()) {
                logger.trace("ending span at", new RuntimeException("this exception is just used to record where the span has been ended from"));
            }
        }
        if (type == null) {
            type = "custom";
        }
        if (transaction != null) {
            transaction.incrementTimer(getType(), getSelfDuration());
        }
        if (parent != null) {
            parent.onChildEnd(this, epochMicros);
        }
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
    }

    @Override
    public String toString() {
        return String.format("'%s' %s", name, traceContext);
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
        final int referenceCount = references.decrementAndGet();
        if (referenceCount == 0) {
            tracer.recycle(this);
        }
        logger.trace("decrement references to {} ({})", this, referenceCount);
    }
}
