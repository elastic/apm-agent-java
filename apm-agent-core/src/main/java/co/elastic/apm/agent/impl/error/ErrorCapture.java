/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.impl.error;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.util.Collection;


/**
 * Data captured by an agent representing an event occurring in a monitored service
 */
public class ErrorCapture implements Recyclable {

    private final TraceContext traceContext;

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
     * Recorded time of the error, UTC based and formatted as YYYY-MM-DDTHH:mm:ss.sssZ
     * (Required)
     */
    private long timestamp;

    /**
     * Provides info about the Transaction corresponding this error
     */
    private TransactionInfo transactionInfo = new TransactionInfo();

    private ElasticApmTracer tracer;
    private final StringBuilder culprit = new StringBuilder();

    public ErrorCapture(ElasticApmTracer tracer) {
        this.tracer = tracer;
        traceContext = TraceContext.with128BitId(this.tracer);
    }

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
     * Creates a reference to a {@link TraceContext}
     *
     * @return {@code this}, for chaining
     * @param parent parent trace context
     */
    public ErrorCapture asChildOf(TraceContextHolder<?> parent) {
        this.traceContext.asChildOf(parent.getTraceContext());
        if (parent instanceof Transaction) {
            Transaction transaction = (Transaction) parent;
            // The error might have occurred in a different thread than the one the transaction was recorded
            // That's why we have to ensure the visibility of the transaction properties
            context.copyFrom(transaction.getContextEnsureVisibility());
        } else if (parent instanceof Span) {
            Span span = (Span) parent;
            // TODO copy into SpanContext
            //  https://github.com/elastic/apm-agent-java/issues/279
            context.copyFrom(span.getContext());
        }
        return this;
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    public void setException(Throwable e) {
        if (WildcardMatcher.anyMatch(tracer.getConfig(CoreConfiguration.class).getUnnestExceptions(), e.getClass().getName()) != null) {
            this.exception = e.getCause();
        } else {
            this.exception = e;
        }
    }

    public StringBuilder getCulprit() {
        // lazily resolve culprit so that java.lang.Throwable.getStackTrace is called outside the application thread
        final Collection<String> applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
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

    public static class TransactionInfo implements Recyclable {
        /**
         * A hint for UI to be able to show whether a recorded trace for the corresponding transaction is expected
         */
        private boolean isSampled;
        /**
         * The related TransactionInfo type
         */
        @Nullable
        private String type;

        @Override
        public void resetState() {
            isSampled = false;
            type = null;
        }

        public boolean isSampled() {
            return isSampled;
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

    public void setTransactionType(@Nullable String type) {
        transactionInfo.type = type;
    }
}
