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
package org.example.stacktrace;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ErrorCaptureTest {

    private StacktraceConfiguration stacktraceConfiguration;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        final ConfigurationRegistry registry = SpyConfiguration.createSpyConfig();
        tracer = MockTracer.create(registry);
        stacktraceConfiguration = registry.getConfig(StacktraceConfiguration.class);
    }

    @Test
    void testCulpritApplicationPackagesNotConfigured() {
        final ErrorCapture errorCapture = new ErrorCapture(tracer);
        errorCapture.setException(new Exception());
        assertThat(errorCapture.getCulprit()).isEmpty();
    }

    @Test
    void testCulprit() {
        when(stacktraceConfiguration.getApplicationPackages()).thenReturn(List.of("org.example.stacktrace"));
        final ErrorCapture errorCapture = new ErrorCapture(tracer);
        final Exception nestedException = new Exception();
        final Exception topLevelException = new Exception(nestedException);
        errorCapture.setException(topLevelException);
        assertThat(errorCapture.getCulprit()).startsWith("org.example.stacktrace.ErrorCaptureTest.testCulprit(ErrorCaptureTest.java:");
        assertThat(errorCapture.getCulprit()).endsWith(":" + nestedException.getStackTrace()[0].getLineNumber() + ")");
    }

    @Test
    void testUnnestNestedExceptions() {
        final ErrorCapture errorCapture = new ErrorCapture(tracer);
        final NestedException nestedException = new NestedException(new Exception());
        errorCapture.setException(nestedException);
        assertThat(errorCapture.getException()).isNotInstanceOf(NestedException.class);
    }

    private static class NestedException extends Exception {
        public NestedException(Throwable cause) {
            super(cause);
        }
    }

    @Test
    void testTransactionContextTransfer() {
        final Transaction transaction = new Transaction(tracer);
        Request transactionRequest = transaction.getContext().getRequest()
            .withMethod("GET")
            .addHeader("key", "value");
        transactionRequest.withBodyBuffer().append("TEST");
        transactionRequest.endOfBufferInput();
        final ErrorCapture errorCapture = new ErrorCapture(tracer).asChildOf(transaction);
        Request errorRequest = errorCapture.getContext().getRequest();
        assertThat(errorRequest.getMethod()).isEqualTo("GET");
        assertThat(errorRequest.getHeaders().get("key")).isEqualTo("value");
        assertThat(errorRequest.getBodyBufferForSerialization()).isNotNull();
        assertThat(errorRequest.getBodyBufferForSerialization().toString()).isEqualTo("TEST");
    }

    @Test
    void testTransactionContextTransferNonFinishedBody() {
        final Transaction transaction = new Transaction(tracer);
        Request transactionRequest = transaction.getContext().getRequest()
            .withMethod("GET")
            .addHeader("key", "value");
        transactionRequest.withBodyBuffer().append("TEST");
        final ErrorCapture errorCapture = new ErrorCapture(tracer).asChildOf(transaction);
        Request errorRequest = errorCapture.getContext().getRequest();
        assertThat(errorRequest.getMethod()).isEqualTo("GET");
        assertThat(errorRequest.getHeaders().get("key")).isEqualTo("value");
        assertThat(errorRequest.getBodyBufferForSerialization())
            .describedAs("Body buffer should be null when copying from a transaction where capturing was not finished yet")
            .isNull();
    }

    @Test
    void testActiveError() {
        assertThat(ErrorCapture.getActive()).isNull();
        ErrorCapture errorCapture = new ErrorCapture(tracer).activate();
        assertThat(ErrorCapture.getActive()).isNotNull();
        errorCapture.deactivate().end();
        assertThat(ErrorCapture.getActive()).isNull();
    }
}
