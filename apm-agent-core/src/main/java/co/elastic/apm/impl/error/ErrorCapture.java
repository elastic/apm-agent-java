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
package co.elastic.apm.impl.error;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.TransactionContext;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.TraceContext;
import co.elastic.apm.objectpool.Recyclable;

import javax.annotation.Nullable;


/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
public class ErrorCapture implements Recyclable {

    private final TraceContext traceContext = new TraceContext();

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    private final TransactionContext context = new TransactionContext();
    /**
     * Information about the originally thrown error.
     */
    @Nullable
    private Throwable exception;
    /**
     * Data for correlating errors with transactions
     */
    @Deprecated
    private final TransactionReference transaction = new TransactionReference();
    /**
     * Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    private long timestamp;
    @Nullable
    private transient ElasticApmTracer tracer;

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    public TransactionContext getContext() {
        return context;
    }

    /**
     * Information about the originally thrown error.
     */
    @Nullable
    public Throwable getException() {
        return exception;
    }

    /**
     * Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    public long getTimestamp() {
        return timestamp;
    }

    public ErrorCapture withTimestamp(long epochMs) {
        this.timestamp = epochMs;
        return this;
    }

    /**
     * Data for correlating errors with transactions
     */
    @Deprecated
    public TransactionReference getTransaction() {
        return transaction;
    }

    @Override
    public void resetState() {
        exception = null;
        context.resetState();
        transaction.resetState();
        timestamp = 0;
        tracer = null;
        traceContext.resetState();
    }

    public void recycle() {
        if (tracer != null) {
            tracer.recycle(this);
        }
    }

    /**
     * Creates a reference to a {@link co.elastic.apm.impl.transaction.Span} or {@link co.elastic.apm.impl.transaction.Transaction}
     *
     * @param parent
     * @return {@code this}, for chaining
     */
    public ErrorCapture asChildOf(AbstractSpan<?> parent) {
        this.traceContext.asChildOf(parent.getTraceContext());
        return this;
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    public void setException(Throwable e) {
        this.exception = e;
    }
}
