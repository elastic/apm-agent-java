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

import co.elastic.apm.impl.transaction.SpanId;
import co.elastic.apm.impl.transaction.TraceId;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.impl.transaction.TransactionId;
import co.elastic.apm.objectpool.Recyclable;


/**
 * Data for correlating errors with transactions
 */
public class TransactionReference implements Recyclable {

    /**
     * ID for the transaction
     */
    private final SpanId id = new SpanId();
    /**
     * ID of the trace forrest
     */
    private final TraceId traceId = new TraceId();
    @Deprecated
    private final TransactionId transactionId = new TransactionId();

    /**
     * UUID for the transaction
     */
    public SpanId getId() {
        return id;
    }

    public TraceId getTraceId() {
        return traceId;
    }

    @Deprecated
    public TransactionId getTransactionId() {
        return transactionId;
    }

    TransactionReference set(Transaction transaction) {
        this.id.copyFrom(transaction.getTraceContext().getId());
        this.traceId.copyFrom(transaction.getTraceContext().getTraceId());
        this.transactionId.copyFrom(transaction.getId());
        return this;
    }

    @Override
    public void resetState() {
        id.resetState();
        traceId.resetState();
    }

    public boolean hasContent() {
        return id.asLong() > 0;
    }
}
