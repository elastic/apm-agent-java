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
package co.elastic.apm.impl.payload;

import co.elastic.apm.TransactionUtils;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.error.ErrorPayload;
import co.elastic.apm.impl.transaction.Transaction;

import static org.mockito.Mockito.mock;

public class PayloadUtils {

    private static final Service SERVICE;
    private static final SystemInfo SYSTEM;
    private static final ProcessInfo PROCESS_INFO;

    static {
        SERVICE = new Service().withAgent(new Agent("name", "version")).withName("name");
        SYSTEM = SystemInfo.create();
        PROCESS_INFO = new ProcessInfo("title");
        PROCESS_INFO.getArgv().add("test");
    }

    public static TransactionPayload createTransactionPayloadWithAllValues() {
        final Transaction transaction = new Transaction(mock(ElasticApmTracer.class));
        TransactionUtils.fillTransaction(transaction);
        final TransactionPayload payload = createTransactionPayload();
        payload.getTransactions().add(transaction);
        return payload;
    }

    public static TransactionPayload createTransactionPayload() {
        return new TransactionPayload(PROCESS_INFO, SERVICE, SYSTEM);
    }

    public static ErrorPayload createErrorPayload() {
        return new ErrorPayload(PROCESS_INFO, SERVICE, SYSTEM);
    }
}
