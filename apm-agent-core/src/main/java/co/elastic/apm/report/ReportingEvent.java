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
package co.elastic.apm.report;

import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;

import javax.annotation.Nullable;

import static co.elastic.apm.report.ReportingEvent.ReportingEventType.ERROR;
import static co.elastic.apm.report.ReportingEvent.ReportingEventType.FLUSH;
import static co.elastic.apm.report.ReportingEvent.ReportingEventType.TRANSACTION;


public class ReportingEvent {
    @Nullable
    private Transaction transaction;
    @Nullable
    private ReportingEventType type;
    @Nullable
    private ErrorCapture error;

    public void resetState() {
        this.transaction = null;
        this.type = null;
        this.error = null;
    }

    @Nullable
    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
        this.type = TRANSACTION;
    }

    public void setFlushEvent() {
        this.type = FLUSH;
    }

    @Nullable
    public ReportingEventType getType() {
        return type;
    }

    @Nullable
    public ErrorCapture getError() {
        return error;
    }

    public void setError(ErrorCapture error) {
        this.error = error;
        this.type = ERROR;
    }

    enum ReportingEventType {
        FLUSH, TRANSACTION, ERROR
    }
}
