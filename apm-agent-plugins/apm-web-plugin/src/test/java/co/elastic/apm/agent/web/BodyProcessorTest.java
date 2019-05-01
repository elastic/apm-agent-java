/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.web;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class BodyProcessorTest {

    private BodyProcessor bodyProcessor;
    private WebConfiguration config;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        bodyProcessor = new BodyProcessor(configurationRegistry);
        config = configurationRegistry.getConfig(WebConfiguration.class);
        tracer = MockTracer.create(configurationRegistry);
    }

    @Test
    void processBeforeReport_Transaction_EventTypeAll() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.ALL);

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody().toString()).isEqualTo("foo");
    }

    @Test
    void processBeforeReport_Transaction_EventTypeTransaction() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.TRANSACTIONS);

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody().toString()).isEqualTo("foo");
    }

    @Test
    void processBeforeReport_Transaction_EventTypeError() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.ERRORS);

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody().toString()).isEqualTo("[REDACTED]");
    }

    @Test
    void processBeforeReport_Transaction_EventTypeOff() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.OFF);

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody().toString()).isEqualTo("[REDACTED]");
    }

    @Test
    void processBeforeReport_Error_EventTypeAll() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.ALL);

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody().toString()).isEqualTo("foo");
    }

    @Test
    void processBeforeReport_Error_EventTypeTransaction() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.TRANSACTIONS);

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody().toString()).isEqualTo("[REDACTED]");
    }

    @Test
    void processBeforeReport_Error_EventTypeError() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.ERRORS);

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody().toString()).isEqualTo("foo");
    }

    @Test
    void processBeforeReport_Error_EventTypeOff() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.OFF);

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody().toString()).isEqualTo("[REDACTED]");
    }

    private Transaction processTransaction() {
        final Transaction transaction = new Transaction(tracer);
        transaction.getContext().getRequest().withBodyBuffer().append("foo").flip();
        bodyProcessor.processBeforeReport(transaction);
        return transaction;
    }

    private ErrorCapture processError() {
        final ErrorCapture error = new ErrorCapture(tracer);
        error.getContext().getRequest().withBodyBuffer().append("foo").flip();
        bodyProcessor.processBeforeReport(error);
        return error;
    }

}
