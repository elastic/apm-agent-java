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
package co.elastic.apm.agent.loginstr.correlation;

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.tracer.Tracer;

@GlobalState
public abstract class AbstractLogCorrelationHelper {

    public static final String TRACE_ID_MDC_KEY = "trace.id";
    public static final String TRANSACTION_ID_MDC_KEY = "transaction.id";
    public static final String ERROR_ID_MDC_KEY = "error.id";

    private static final CallDepth callDepth = CallDepth.get(AbstractLogCorrelationHelper.class);

    /**
     * Invokes the addition of active context metadata to the MDC (or framework-equivalent)
     * @return returns {@code true} if the active context metadata was added to the MDC
     */
    public boolean beforeLoggingEvent() {
        if (callDepth.isNestedCallAndIncrement()) {
            return false;
        }
        return addToMdc();
    }

    /**
     * Clears context metadata from the MDC if required
     * @param addedToMdc should reflect the value returned from {@link #beforeLoggingEvent()} for the corresponding API call
     */
    public void afterLoggingEvent(boolean addedToMdc) {
        if (!callDepth.isNestedCallAndDecrement() && addedToMdc) {
            removeFromMdc();
        }
    }

    /**
     * Add details of the currently active context to the logging framework MDC (or framework-equivalent)
     * @return {@code true} if context metadata was added to the MDC, otherwise should return {@code false}
     */
    protected abstract boolean addToMdc();

    protected abstract void removeFromMdc();

    /**
     * Default abstract implementation for {@link AbstractLogCorrelationHelper}, which retrieves the currently active context and
     * adds metadata key-by-key.
     */
    public static abstract class DefaultLogCorrelationHelper extends AbstractLogCorrelationHelper {

        private final Tracer tracer = GlobalTracer.get();

        @Override
        protected boolean addToMdc() {
            boolean addedToMdc = false;
            AbstractSpan<?> activeSpan = tracer.getActive();
            if (activeSpan != null) {
                addToMdc(TRACE_ID_MDC_KEY, activeSpan.getTraceContext().getTraceId().toString());
                addToMdc(TRANSACTION_ID_MDC_KEY, activeSpan.getTraceContext().getTransactionId().toString());
                addedToMdc = true;
            }
            ErrorCapture activeError = ErrorCapture.getActive();
            if (activeError != null) {
                addToMdc(ERROR_ID_MDC_KEY, activeError.getTraceContext().getId().toString());
                addedToMdc = true;
            }
            return addedToMdc;
        }

        @Override
        protected void removeFromMdc() {
            removeFromMdc(TRACE_ID_MDC_KEY);
            removeFromMdc(TRANSACTION_ID_MDC_KEY);
            removeFromMdc(ERROR_ID_MDC_KEY);
        }

        protected abstract void addToMdc(String key, String value);

        protected abstract void removeFromMdc(String key);
    }
}
