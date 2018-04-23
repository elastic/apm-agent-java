/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.transaction.TransactionId;
import co.elastic.apm.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.util.Date;


/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
public class ErrorCapture implements Recyclable {

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    private final Context context = new Context();
    /**
     * Information about the originally thrown error.
     */
    private final ExceptionInfo exception = new ExceptionInfo();
    /**
     * Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    private final Date timestamp = new Date();
    /**
     * Data for correlating errors with transactions
     */
    private final TransactionReference transaction = new TransactionReference();
    @Nullable
    private transient ElasticApmTracer tracer;
    /**
     * ID for the error
     */
    private final TransactionId id = new TransactionId();

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    public Context getContext() {
        return context;
    }

    /**
     * Information about the originally thrown error.
     */
    public ExceptionInfo getException() {
        return exception;
    }

    /**
     * UUID for the error
     */
    public TransactionId getId() {
        return id;
    }

    /**
     * Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    public Date getTimestamp() {
        return timestamp;
    }

    public ErrorCapture withTimestamp(long epochMs) {
        this.timestamp.setTime(epochMs);
        return this;
    }

    /**
     * Data for correlating errors with transactions
     */
    public TransactionReference getTransaction() {
        return transaction;
    }

    @Override
    public void resetState() {
        exception.resetState();
        context.resetState();
        id.resetState();
        transaction.resetState();
        timestamp.setTime(0);
        tracer = null;
    }

    public void recycle() {
        if (tracer != null) {
            tracer.recycle(this);
        }
    }
}
