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
package co.elastic.apm.web;

import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BodyProcessorTest {

    private BodyProcessor bodyProcessor;
    private WebConfiguration config;

    @BeforeEach
    void setUp() {
        bodyProcessor = new BodyProcessor();
        ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        bodyProcessor.init(configurationRegistry);
        config = configurationRegistry.getConfig(WebConfiguration.class);
    }

    @Test
    void processBeforeReport_Transaction_EventTypeAll() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.ALL);

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody()).isEqualTo("foo");
    }

    @Test
    void processBeforeReport_Transaction_EventTypeTransaction() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.TRANSACTIONS);

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody()).isEqualTo("foo");
    }

    @Test
    void processBeforeReport_Transaction_EventTypeError() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.ERRORS);

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody()).isEqualTo("[REDACTED]");
    }

    @Test
    void processBeforeReport_Transaction_EventTypeOff() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.OFF);

        final Transaction transaction = processTransaction();

        assertThat(transaction.getContext().getRequest().getBody()).isEqualTo("[REDACTED]");
    }

    @Test
    void processBeforeReport_Error_EventTypeAll() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.ALL);

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody()).isEqualTo("foo");
    }

    @Test
    void processBeforeReport_Error_EventTypeTransaction() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.TRANSACTIONS);

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody()).isEqualTo("[REDACTED]");
    }

    @Test
    void processBeforeReport_Error_EventTypeError() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.ERRORS);

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody()).isEqualTo("foo");
    }

    @Test
    void processBeforeReport_Error_EventTypeOff() {
        when(config.getCaptureBody()).thenReturn(WebConfiguration.EventType.OFF);

        final ErrorCapture error = processError();

        assertThat(error.getContext().getRequest().getBody()).isEqualTo("[REDACTED]");
    }

    private Transaction processTransaction() {
        final Transaction transaction = new Transaction(mock(ElasticApmTracer.class));
        transaction.getContext().getRequest().withRawBody("foo");
        bodyProcessor.processBeforeReport(transaction);
        return transaction;
    }

    private ErrorCapture processError() {
        final ErrorCapture error = new ErrorCapture(mock(ElasticApmTracer.class));
        error.getContext().getRequest().withRawBody("foo");
        bodyProcessor.processBeforeReport(error);
        return error;
    }

}
