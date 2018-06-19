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

import co.elastic.apm.impl.transaction.TransactionId;
import co.elastic.apm.objectpool.Recyclable;


/**
 * Data for correlating errors with transactions
 */
public class TransactionReference implements Recyclable {

    private final TransactionId transactionId = new TransactionId();

    public TransactionId getTransactionId() {
        return transactionId;
    }

    @Override
    public void resetState() {
        transactionId.resetState();
    }

    public boolean hasContent() {
        return !transactionId.isEmpty();
    }
}
