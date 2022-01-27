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

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.state.CallDepth;

import javax.annotation.Nullable;

public abstract class AbstractLogCorrelationHelper {

    private static final CallDepth callDepth = CallDepth.get(AbstractLogCorrelationHelper.class);

    public static final String TRACE_ID_MDC_KEY = "trace.id";
    public static final String TRANSACTION_ID_MDC_KEY = "transaction.id";

    /**
     * Adds the active transaction's ID and trace ID to the MDC in the outmost logging API call
     * @param activeTransaction the currently active transaction, or {@code null} if there is no such
     * @return returns {@code true} if the transaction IDs were added to the MDC
     */
    public boolean beforeLoggingApiCall(@Nullable Transaction activeTransaction) {
        if (callDepth.isNestedCallAndIncrement() || activeTransaction == null) {
            return false;
        }
        addToMdc(TRACE_ID_MDC_KEY, activeTransaction.getTraceContext().getTraceId().toString());
        addToMdc(TRANSACTION_ID_MDC_KEY, activeTransaction.getTraceContext().getTransactionId().toString());
        return true;
    }

    /**
     * Clears transaction and trace ID from the MDC if required
     * @param added should reflect the value returned from {@link #beforeLoggingApiCall(Transaction)} for the corresponding API call
     */
    public void afterLoggingApi(boolean added) {
        if (callDepth.isNestedCallAndDecrement() && added) {
            removeFromMdc(TRACE_ID_MDC_KEY);
            removeFromMdc(TRANSACTION_ID_MDC_KEY);
        }
    }

    protected abstract void addToMdc(String key, String value);

    protected abstract void removeFromMdc(String key);
}
