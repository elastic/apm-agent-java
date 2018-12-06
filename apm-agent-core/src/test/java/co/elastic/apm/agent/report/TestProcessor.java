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
package co.elastic.apm.report;

import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.processor.Processor;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.concurrent.atomic.AtomicInteger;

public class TestProcessor implements Processor {

    private static AtomicInteger transactionCounter = new AtomicInteger();
    private static AtomicInteger errorCounter = new AtomicInteger();

    @Override
    public void init(ConfigurationRegistry configurationRegistry) {

    }

    @Override
    public void processBeforeReport(Transaction transaction) {
        transactionCounter.incrementAndGet();
    }

    @Override
    public void processBeforeReport(ErrorCapture error) {
        errorCounter.incrementAndGet();
    }

    public static int getTransactionCount() {
        return transactionCounter.get();
    }

    public static int getErrorCount() {
        return errorCounter.get();
    }
}
