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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static co.elastic.apm.agent.impl.context.AbstractContext.REDACTED_CONTEXT_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

class BodyProcessorTest {

    private BodyProcessor bodyProcessor;
    private CoreConfiguration config;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        bodyProcessor = new BodyProcessor(configurationRegistry);
        config = configurationRegistry.getConfig(CoreConfiguration.class);
        tracer = MockTracer.create(configurationRegistry);
    }

    @Test
    void processBeforeReport_Transaction_EventTypeAll() {
        doReturn(CoreConfiguration.EventType.ALL).when(config).getCaptureBody();

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody().toString()).isEqualTo("foo");
        assertThat(transaction.getContext().getMessage().getBodyForRead().toString()).isEqualTo("bar");
    }

    @Test
    void processBeforeReport_Transaction_EventTypeTransaction() {
        doReturn(CoreConfiguration.EventType.TRANSACTIONS).when(config).getCaptureBody();

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody().toString()).isEqualTo("foo");
        assertThat(transaction.getContext().getMessage().getBodyForRead().toString()).isEqualTo("bar");
    }

    @Test
    void processBeforeReport_Transaction_EventTypeError() {
        doReturn(CoreConfiguration.EventType.ERRORS).when(config).getCaptureBody();

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody().toString()).isEqualTo(REDACTED_CONTEXT_STRING);
        assertThat(transaction.getContext().getMessage().getBodyForRead().toString()).isEqualTo(REDACTED_CONTEXT_STRING);
    }

    @Test
    void processBeforeReport_Transaction_EventTypeOff() {
        doReturn(CoreConfiguration.EventType.OFF).when(config).getCaptureBody();

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody().toString()).isEqualTo(REDACTED_CONTEXT_STRING);
        assertThat(transaction.getContext().getMessage().getBodyForRead().toString()).isEqualTo(REDACTED_CONTEXT_STRING);
    }

    @Test
    void processBeforeReport_Error_EventTypeAll() {
        doReturn(CoreConfiguration.EventType.ALL).when(config).getCaptureBody();

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody().toString()).isEqualTo("foo");
        assertThat(error.getContext().getMessage().getBodyForRead().toString()).isEqualTo("bar");
    }

    @Test
    void processBeforeReport_Error_EventTypeTransaction() {
        doReturn(CoreConfiguration.EventType.TRANSACTIONS).when(config).getCaptureBody();

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody().toString()).isEqualTo(REDACTED_CONTEXT_STRING);
        assertThat(error.getContext().getMessage().getBodyForRead().toString()).isEqualTo(REDACTED_CONTEXT_STRING);
    }

    @Test
    void processBeforeReport_Error_EventTypeError() {
        doReturn(CoreConfiguration.EventType.ERRORS).when(config).getCaptureBody();

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody().toString()).isEqualTo("foo");
        assertThat(error.getContext().getMessage().getBodyForRead().toString()).isEqualTo("bar");
    }

    @Test
    void processBeforeReport_Error_EventTypeOff() {
        doReturn(CoreConfiguration.EventType.OFF).when(config).getCaptureBody();

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody().toString()).isEqualTo(REDACTED_CONTEXT_STRING);
        assertThat(error.getContext().getMessage().getBodyForRead().toString()).isEqualTo(REDACTED_CONTEXT_STRING);
    }

    private Transaction processTransaction() {
        final Transaction transaction = new Transaction(tracer);
        Request request = transaction.getContext().getRequest();
        request.withBodyBuffer().append("foo");
        request.endOfBufferInput();
        transaction.getContext().getMessage().withBody("bar");
        bodyProcessor.processBeforeReport(transaction);
        return transaction;
    }

    private ErrorCapture processError() {
        final ErrorCapture error = new ErrorCapture(tracer);
        Request request = error.getContext().getRequest();
        request.withBodyBuffer().append("foo");
        request.endOfBufferInput();
        error.getContext().getMessage().withBody("bar");
        bodyProcessor.processBeforeReport(error);
        return error;
    }

}
