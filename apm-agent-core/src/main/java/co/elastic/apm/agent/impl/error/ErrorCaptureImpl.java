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
package co.elastic.apm.agent.impl.error;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.TransactionContextImpl;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfigurationImpl;
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TraceContextImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.ErrorCapture;
import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;
import java.util.Collection;


/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
public class ErrorCaptureImpl implements Recyclable, ErrorCapture {

    private static final Logger logger = LoggerFactory.getLogger(ErrorCaptureImpl.class);

    private static final ThreadLocal<ErrorCaptureImpl> activeError = new ThreadLocal<>();

    private final TraceContextImpl traceContext;

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    private final TransactionContextImpl context = new TransactionContextImpl();
    private final ElasticApmTracer tracer;
    /**
     * Information about the originally thrown error.
     */
    @Nullable
    private Throwable exception;
    /**
     * Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    private long timestamp;

    /**
     * Provides info about the Transaction corresponding this error
     */
    private TransactionInfo transactionInfo = new TransactionInfo();

    private final StringBuilder culprit = new StringBuilder();

    public ErrorCaptureImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
        traceContext = TraceContextImpl.with128BitId(this.tracer);
    }

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    public TransactionContextImpl getContext() {
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

    public ErrorCaptureImpl withTimestamp(long epochMs) {
        this.timestamp = epochMs;
        return this;
    }

    @Override
    public void resetState() {
        exception = null;
        context.resetState();
        timestamp = 0;
        transactionInfo.resetState();
        traceContext.resetState();
        culprit.setLength(0);
    }

    public void recycle() {
        tracer.recycle(this);
    }

    /**
     * Creates a reference to a {@link TraceContextImpl}
     *
     * @param parent parent trace context
     * @return {@code this}, for chaining
     */
    public ErrorCaptureImpl asChildOf(AbstractSpanImpl<?> parent) {
        this.traceContext.asChildOf(parent.getTraceContext());
        if (traceContext.getTraceId().isEmpty()) {
            logger.debug("Creating an Error as child of {} with a null trace_id", parent.getNameAsString());
            if (logger.isTraceEnabled()) {
                logger.trace("Stack trace related to Error capture: ", new Throwable());
            }
        }
        if (parent instanceof TransactionImpl) {
            TransactionImpl transaction = (TransactionImpl) parent;
            // The error might have occurred in a different thread than the one the transaction was recorded
            // That's why we have to ensure the visibility of the transaction properties
            context.copyFrom(transaction.getContextEnsureVisibility());
        } else if (parent instanceof SpanImpl) {
            SpanImpl span = (SpanImpl) parent;
            // TODO copy into SpanContext
            //  https://github.com/elastic/apm-agent-java/issues/279
            context.copyFrom(span.getContext());
        }
        return this;
    }

    @Override
    public TraceContextImpl getTraceContext() {
        return traceContext;
    }

    public void setException(Throwable e) {
        exception = e;
    }

    public StringBuilder getCulprit() {
        // lazily resolve culprit so that java.lang.Throwable.getStackTrace is called outside the application thread
        final Collection<String> applicationPackages = tracer.getConfig(StacktraceConfigurationImpl.class).getApplicationPackages();
        if (exception != null && culprit.length() == 0 && !applicationPackages.isEmpty()) {
            computeCulprit(exception, applicationPackages);
        }
        return culprit;
    }

    private void computeCulprit(Throwable exception, Collection<String> applicationPackages) {
        if (exception.getCause() != null) {
            computeCulprit(exception.getCause(), applicationPackages);
        }
        if (culprit.length() > 0) {
            return;
        }
        for (StackTraceElement stackTraceElement : exception.getStackTrace()) {
            for (String applicationPackage : applicationPackages) {
                if (stackTraceElement.getClassName().startsWith(applicationPackage)) {
                    setCulprit(stackTraceElement);
                    return;
                }
            }
        }
    }

    private void setCulprit(StackTraceElement stackTraceElement) {
        final int lineNumber = stackTraceElement.getLineNumber();
        final String fileName = stackTraceElement.getFileName();
        culprit.append(stackTraceElement.getClassName())
            .append('.')
            .append(stackTraceElement.getMethodName())
            .append('(');
        if (stackTraceElement.isNativeMethod()) {
            culprit.append("Native Method");
        } else {
            culprit.append(fileName != null ? fileName : "Unknown Source");
            if (lineNumber > 0) {
                culprit.append(':').append(lineNumber);
            }
        }
        culprit.append(')');
    }

    @Override
    public ErrorCaptureImpl activate() {
        activeError.set(this);
        return this;
    }

    @Override
    public ErrorCaptureImpl deactivate() {
        activeError.remove();
        return this;
    }

    @Nullable
    public static ErrorCaptureImpl getActive() {
        return activeError.get();
    }

    public static class TransactionInfo implements Recyclable {
        /**
         * A hint for UI to be able to show whether a recorded trace for the corresponding transaction is expected
         */
        private boolean isSampled;
        /**
         * The related TransactionInfo name
         */
        private StringBuilder name = new StringBuilder();
        /**
         * The related TransactionInfo type
         */
        @Nullable
        private String type;

        @Override
        public void resetState() {
            isSampled = false;
            name.setLength(0);
            type = null;
        }

        public boolean isSampled() {
            return isSampled;
        }

        public StringBuilder getName() {
            return name;
        }

        @Nullable
        public String getType() {
            return type;
        }
    }

    public TransactionInfo getTransactionInfo() {
        return transactionInfo;
    }

    public void setTransactionSampled(boolean transactionSampled) {
        transactionInfo.isSampled = transactionSampled;
    }

    public void setTransactionName(@Nullable CharSequence name) {
        transactionInfo.name.setLength(0);
        transactionInfo.name.append(name);
    }

    public void setTransactionType(@Nullable String type) {
        transactionInfo.type = type;
    }

    @Override
    public void end() {
        tracer.endError(this);
    }
}
