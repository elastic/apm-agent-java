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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.dslplatform.json.JsonWriter;

import javax.annotation.Nullable;
import java.util.concurrent.locks.LockSupport;

import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.BYTES_LOG;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.END_REQUEST;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.ERROR;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.JSON_WRITER;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.MAKE_FLUSH_REQUEST;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.SHUTDOWN;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.SPAN;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.STRING_LOG;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.TRANSACTION;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.WAKEUP;

public class ReportingEvent {
    @Nullable
    private ReportingEventType type;
    @Nullable
    private Object event;
    @Nullable
    private Thread unparkAfterProcessed;

    public void resetState() {
        this.event = null;
        this.unparkAfterProcessed = null;
    }

    @Nullable
    public Transaction getTransaction() {
        if (type == TRANSACTION) {
            return (Transaction) event;
        } else {
            return null;
        }
    }

    public void setTransaction(Transaction transaction) {
        this.event = transaction;
        this.type = TRANSACTION;
    }

    public void setEndRequestEvent() {
        this.type = END_REQUEST;
    }

    public void setMakeFlushRequestEvent() {
        this.type = MAKE_FLUSH_REQUEST;
    }

    @Nullable
    public ReportingEventType getType() {
        return type;
    }

    @Nullable
    public ErrorCapture getError() {
        if (type == ERROR) {
            return (ErrorCapture) event;
        } else {
            return null;
        }
    }

    @Nullable
    public Span getSpan() {
        if (type == SPAN) {
            return (Span) event;
        } else {
            return null;
        }
    }

    public void setError(ErrorCapture error) {
        this.event = error;
        this.type = ERROR;
    }

    public void setSpan(Span span) {
        this.event = span;
        this.type = SPAN;
    }

    public void setString(String string) {
        this.event = string;
        this.type = STRING_LOG;
    }

    @Nullable
    public String getStringLog() {
        if (type == STRING_LOG) {
            return (String) event;
        } else {
            return null;
        }
    }
    public void setBytes(byte[] bytes) {
        this.event = bytes;
        this.type = BYTES_LOG;
    }

    @Nullable
    public byte[] getBytesLog() {
        if (type == BYTES_LOG) {
            return (byte[]) event;
        } else {
            return null;
        }
    }
    public void shutdownEvent() {
        this.type = SHUTDOWN;
    }

    @Override
    public String toString() {
        StringBuilder description = new StringBuilder();
        description.append("Type: ").append(type);
        if (event instanceof AbstractSpan<?>) {
            description.append(", ").append(event);
        }
        return description.toString();
    }

    @Nullable
    public JsonWriter getJsonWriter() {
        if (getType() == JSON_WRITER) {
            return (JsonWriter) event;
        } else {
            return null;
        }
    }
    public void setJsonWriter(@Nullable JsonWriter jsonWriter) {
        this.event = jsonWriter;
        this.type = JSON_WRITER;
    }

    public void end() {
        if (event instanceof AbstractSpan<?>) {
            ((AbstractSpan<?>) event).decrementReferences();
        } else if (event instanceof ErrorCapture) {
            ((ErrorCapture) event).recycle();
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

    public Object getEvent() {
        return event;
    }

    enum ReportingEventType {
        // control events
        END_REQUEST,
        MAKE_FLUSH_REQUEST,
        SHUTDOWN,
        WAKEUP,

        // payload events
        TRANSACTION,
        SPAN,
        ERROR,
        JSON_WRITER,
        STRING_LOG,
        BYTES_LOG
    }
}
