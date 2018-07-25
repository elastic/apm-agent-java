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

import co.elastic.apm.impl.error.ErrorPayload;
import co.elastic.apm.impl.payload.Payload;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.payload.TransactionPayload;
import co.elastic.apm.report.processor.ProcessorEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static co.elastic.apm.report.ReportingEvent.ReportingEventType.ERROR;
import static co.elastic.apm.report.ReportingEvent.ReportingEventType.FLUSH;
import static co.elastic.apm.report.ReportingEvent.ReportingEventType.SPAN;
import static co.elastic.apm.report.ReportingEvent.ReportingEventType.TRANSACTION;

/**
 * Supports the /v1/error and /v1/transactions endpoints of the intake API v1
 */
public class IntakeV1ReportingEventHandler implements ReportingEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(IntakeV1ReportingEventHandler.class);
    private final TransactionPayload transactionPayload;
    private final ErrorPayload errorPayload;
    private final PayloadSender payloadSender;
    private final ReporterConfiguration reporterConfiguration;
    private final ProcessorEventHandler processorEventHandler;

    public IntakeV1ReportingEventHandler(Service service, ProcessInfo process, SystemInfo system, PayloadSender payloadSender,
                                         ReporterConfiguration reporterConfiguration, ProcessorEventHandler processorEventHandler) {
        this.payloadSender = payloadSender;
        this.reporterConfiguration = reporterConfiguration;
        this.processorEventHandler = processorEventHandler;
        transactionPayload = new TransactionPayload(process, service, system);
        errorPayload = new ErrorPayload(process, service, system);
    }

    @Override
    public void onEvent(ReportingEvent event, long sequence, boolean endOfBatch) {
        if (logger.isTraceEnabled()) {
            logger.trace("Receiving {} event (sequence {})", event.getType(), sequence);
        }
        processorEventHandler.onEvent(event, sequence, endOfBatch);
        if (event.getType() == FLUSH) {
            flush(transactionPayload);
            flush(errorPayload);
        }
        if (event.getType() == TRANSACTION) {
            transactionPayload.getTransactions().add(event.getTransaction());
            if (transactionPayload.getTransactions().size() >= reporterConfiguration.getMaxQueueSize()) {
                flush(transactionPayload);
            }
        }
        if (event.getType() == SPAN) {
            transactionPayload.getSpans().add(event.getSpan());
        }
        if (event.getType() == ERROR) {
            errorPayload.getErrors().add(event.getError());
            // report errors immediately, except if there are multiple in the queue
            if (endOfBatch) {
                flush(errorPayload);
            }
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Finished processing {} event (sequence {})", event.getType(), sequence);
        }
        event.resetState();
    }

    private void flush(Payload payload) {
        if (payload.getPayloadObjects().isEmpty()) {
            return;
        }

        try {
            payloadSender.sendPayload(payload);
        } finally {
            payload.resetState();
        }

    }

    @Override
    public long getReported() {
        return payloadSender.getReported();
    }

    @Override
    public long getDropped() {
        return payloadSender.getDropped();
    }
}
