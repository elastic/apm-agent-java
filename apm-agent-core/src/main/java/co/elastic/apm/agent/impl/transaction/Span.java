/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
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
import co.elastic.apm.agent.impl.context.SpanContext;
import co.elastic.apm.agent.objectpool.Recyclable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class Span extends AbstractSpan<Span> implements Recyclable {

    private static final Logger logger = LoggerFactory.getLogger(Span.class);

    /**
     * Any other arbitrary data captured by the agent, optionally provided by the user
     */
    private final SpanContext context = new SpanContext();
    @Nullable
    private Throwable stacktrace;

    public Span(ElasticApmTracer tracer) {
        super(tracer);
    }

    public <T> Span start(TraceContext.ChildContextCreator<T> childContextCreator, T parentContext) {
        // we can't get the timestamp here as the clock has not yet been initialized
        return start(childContextCreator, parentContext, -1);
    }

    public <T> Span start(TraceContext.ChildContextCreator<T> childContextCreator, T parentContext, long epochMicros) {
        return start(childContextCreator, parentContext, epochMicros, false);
    }

    public <T> Span start(TraceContext.ChildContextCreator<T> childContextCreator, T parentContext, long epochMicros, boolean dropped) {
        onStart();
        childContextCreator.asChildOf(traceContext, parentContext);
        if (dropped) {
            traceContext.setRecorded(false);
        }
        if (epochMicros >= 0) {
            timestamp = epochMicros;
        } else {
            timestamp = getTraceContext().getClock().getEpochMicros();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("startSpan {} {", this);
            if (logger.isTraceEnabled()) {
                logger.trace("starting span at",
                    new RuntimeException("this exception is just used to record where the span has been started from"));
            }
        }
        return this;
    }

    /**
     * Any other arbitrary data captured by the agent, optionally provided by the user
     */
    public SpanContext getContext() {
        return context;
    }

    public Span withName(@Nullable String name) {
        setName(name);
        return this;
    }

    @Nullable
    public Throwable getStacktrace() {
        return stacktrace;
    }

    @Override
    public void doEnd(long epochMicros) {
        if (logger.isDebugEnabled()) {
            logger.debug("} endSpan {}", this);
            if (logger.isTraceEnabled()) {
                logger.trace("ending span at", new RuntimeException("this exception is just used to record where the span has been ended from"));
            }
        }
        this.tracer.endSpan(this);
    }

    @Override
    public void resetState() {
        super.resetState();
        context.resetState();
        stacktrace = null;
    }

    @Override
    public void addTag(String key, String value) {
        context.getTags().put(key, value);
    }

    public void recycle() {
        tracer.recycle(this);
    }

    @Override
    public String toString() {
        return String.format("'%s' %s", name, traceContext);
    }

    public Span withStacktrace(Throwable stacktrace) {
        this.stacktrace = stacktrace;
        return this;
    }
}
