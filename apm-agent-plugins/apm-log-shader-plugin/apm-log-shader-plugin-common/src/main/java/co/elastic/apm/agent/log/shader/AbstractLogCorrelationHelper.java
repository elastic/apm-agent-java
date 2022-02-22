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
package co.elastic.apm.agent.log.shader;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.sdk.state.GlobalState;

@GlobalState
public abstract class AbstractLogCorrelationHelper {

    public static final String TRACE_ID_MDC_KEY = "trace.id";
    public static final String TRANSACTION_ID_MDC_KEY = "transaction.id";

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
        if (callDepth.isNestedCallAndDecrement() && addedToMdc) {
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
            Transaction activeTransaction = tracer.currentTransaction();
            if (activeTransaction == null) {
                return false;
            }
            boolean added = addToMdc(TRACE_ID_MDC_KEY, activeTransaction.getTraceContext().getTraceId().toString());
            added |= addToMdc(TRANSACTION_ID_MDC_KEY, activeTransaction.getTraceContext().getTransactionId().toString());
            return added;
        }

        @Override
        protected void removeFromMdc() {
            removeFromMdc(TRACE_ID_MDC_KEY);
            removeFromMdc(TRANSACTION_ID_MDC_KEY);
        }

        protected abstract boolean addToMdc(String key, String value);

        protected abstract void removeFromMdc(String key);
    }
}
