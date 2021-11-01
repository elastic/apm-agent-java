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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.dslplatform.json.JsonWriter;

import javax.annotation.Nullable;
import java.util.concurrent.locks.LockSupport;

import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.END_REQUEST;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.ERROR;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.JSON_WRITER;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.SHUTDOWN;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.SPAN;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.TRANSACTION;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.WAKEUP;

public class ReportingEvent {
    @Nullable
    private Transaction transaction;
    @Nullable
    private ReportingEventType type;
    @Nullable
    private ErrorCapture error;
    @Nullable
    private Span span;
    @Nullable
    private JsonWriter jsonWriter;
    @Nullable
    private Thread unparkAfterProcessed;

    public void resetState() {
        this.transaction = null;
        this.type = null;
        this.error = null;
        this.span = null;
        this.jsonWriter = null;
        this.unparkAfterProcessed = null;
    }

    @Nullable
    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
        this.type = TRANSACTION;
    }

    public void setEndRequestEvent() {
        this.type = END_REQUEST;
    }

    @Nullable
    public ReportingEventType getType() {
        return type;
    }

    @Nullable
    public ErrorCapture getError() {
        return error;
    }

    @Nullable
    public Span getSpan() {
        return span;
    }

    public void setError(ErrorCapture error) {
        this.error = error;
        this.type = ERROR;
    }

    public void setSpan(Span span) {
        this.span = span;
        this.type = SPAN;
    }

    public void shutdownEvent() {
        this.type = SHUTDOWN;
    }

    @Override
    public String toString() {
        StringBuilder description = new StringBuilder();
        description.append("Type: ").append(type);
        if (transaction != null) {
            description.append(", ").append(transaction.toString());
        } else if (span != null) {
            description.append(", ").append(span.toString());
        }
        return description.toString();
    }

    @Nullable
    public JsonWriter getJsonWriter() {
        return jsonWriter;
    }

    public void setJsonWriter(@Nullable JsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
        this.type = JSON_WRITER;
    }

    public void end() {
        if (transaction != null) {
            transaction.decrementReferences();
        } else if (span != null) {
            span.decrementReferences();
        } else if (error != null) {
            error.recycle();
        }
        if (unparkAfterProcessed != null) {
            LockSupport.unpark(unparkAfterProcessed);
        }
    }

    public void unparkAfterProcessed(@Nullable Thread thread) {
        unparkAfterProcessed = thread;
    }

    public void setWakeupEvent() {
        type = WAKEUP;
    }

    enum ReportingEventType {
        END_REQUEST, TRANSACTION, SPAN, ERROR, SHUTDOWN, JSON_WRITER, WAKEUP
    }
}
