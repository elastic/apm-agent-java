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

import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import com.dslplatform.json.JsonWriter;

import javax.annotation.Nullable;
import java.util.concurrent.locks.LockSupport;

import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.BYTES_LOG;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.END_REQUEST;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.ERROR;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.MAKE_FLUSH_REQUEST;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.METRICSET_JSON_WRITER;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.SHUTDOWN;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.SPAN;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.STRING_LOG;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.TRANSACTION;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.WAKEUP;

public class ReportingEvent {
    @Nullable
    private TransactionImpl transaction;
    @Nullable
    private ReportingEventType type;

    @Nullable
    private ErrorCaptureImpl error;
    @Nullable
    private SpanImpl span;
    @Nullable
    private JsonWriter jsonWriter;
    @Nullable
    private Thread unparkAfterProcessed;

    @Nullable
    private String stringLog;
    @Nullable
    private byte[] bytesLog;
    private boolean agentLog;

    public void resetState() {
        this.transaction = null;
        this.type = null;
        this.error = null;
        this.span = null;
        this.jsonWriter = null;
        this.unparkAfterProcessed = null;
        this.agentLog = false;
        this.bytesLog = null;
        this.stringLog = null;
    }

    @Nullable
    public TransactionImpl getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionImpl transaction) {
        this.transaction = transaction;
        this.type = TRANSACTION;
    }

    public void setEndRequestEvent() {
        this.type = END_REQUEST;
    }

    public void setMakeFlushRequestEvent() {
        this.type = MAKE_FLUSH_REQUEST;
    }

    public ReportingEventType getType() {
        if (type == null) {
            throw new IllegalStateException("ReportingEvent is not initialized!");
        }
        return type;
    }

    @Nullable
    public ErrorCaptureImpl getError() {
        return error;
    }

    @Nullable
    public SpanImpl getSpan() {
        return span;
    }

    public void setError(ErrorCaptureImpl error) {
        this.error = error;
        this.type = ERROR;
    }

    public void setSpan(SpanImpl span) {
        this.span = span;
        this.type = SPAN;
    }

    public void setStringLog(String string) {
        this.stringLog = string;
        this.type = STRING_LOG;
    }

    @Nullable
    public String getStringLog() {
        return stringLog;
    }
    public void setBytesLog(byte[] bytes, boolean agentLog) {
        this.bytesLog = bytes;
        this.type = BYTES_LOG;
        this.agentLog = agentLog;
    }

    @Nullable
    public byte[] getBytesLog() {
        return bytesLog;
    }

    public boolean isAgentLog() {
        return type != null
            && (type == BYTES_LOG || type == STRING_LOG)
            && agentLog;
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

    public void setMetricSet(@Nullable JsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
        this.type = METRICSET_JSON_WRITER;
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

    public enum ReportingEventType {
        // control events
        END_REQUEST(true),
        MAKE_FLUSH_REQUEST(true),
        SHUTDOWN(true),
        WAKEUP(true),

        // payload events,
        TRANSACTION(false),
        SPAN(false),
        ERROR(false),
        METRICSET_JSON_WRITER(false),
        STRING_LOG(false),
        BYTES_LOG(false);

        private final boolean control;

        /**
         * @param control {@literal true} for control events
         */
        ReportingEventType(boolean control) {
            this.control = control;
        }

        public boolean isControl() {
            return control;
        }
    }


}
