/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.BYTES;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.ERROR;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.FLUSH;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.METRICS;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.SHUTDOWN;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.SPAN;
import static co.elastic.apm.agent.report.ReportingEvent.ReportingEventType.TRANSACTION;

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
    private MetricRegistry metricRegistry;
    @Nullable
    private byte[] bytes;

    public void resetState() {
        this.transaction = null;
        this.type = null;
        this.error = null;
        this.span = null;
        this.metricRegistry = null;
        this.bytes = null;
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

    public void reportMetrics(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        this.type = METRICS;
    }

    public void shutdownEvent() {
        this.type = SHUTDOWN;
    }

    @Nullable
    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
        this.type = BYTES;
    }

    @Nullable
    public byte[] getBytes() {
        return bytes;
    }

    enum ReportingEventType {
        FLUSH, TRANSACTION, SPAN, ERROR, METRICS, SHUTDOWN, BYTES
    }
}
