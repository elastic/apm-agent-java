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
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

class ErrorCaptureTest {

    private ElasticApmTracer tracer;
    private StacktraceConfiguration stacktraceConfiguration;
    private CoreConfiguration coreConfiguration;

    @BeforeEach
    void setUp() {
        tracer = MockTracer.createRealTracer();
        stacktraceConfiguration = tracer.getConfig(StacktraceConfiguration.class);
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
    }

    @Test
    void testCulpritApplicationPackagesNotConfigured() {
        final ErrorCapture errorCapture = new ErrorCapture(tracer);
        errorCapture.setException(new Exception());
        assertThat(errorCapture.getCulprit()).isEmpty();
    }

    @Test
    void testCulprit() {
        doReturn(List.of("org.example.stacktrace")).when(stacktraceConfiguration).getApplicationPackages();
        final ErrorCapture errorCapture = new ErrorCapture(tracer);
        final Exception nestedException = new Exception();
        final Exception topLevelException = new Exception(nestedException);
        errorCapture.setException(topLevelException);
        assertThat(errorCapture.getCulprit()).startsWith("org.example.stacktrace.ErrorCaptureTest.testCulprit(ErrorCaptureTest.java:");
        assertThat(errorCapture.getCulprit()).endsWith(":" + nestedException.getStackTrace()[0].getLineNumber() + ")");
    }

    @Test
    void testUnnestNestedException() {
        final NestedException nestedException = new NestedException(new CustomException());
        ErrorCapture errorCapture = tracer.captureException(nestedException, null, null);
        assertThat(errorCapture).isNotNull();
        assertThat(errorCapture.getException()).isNotInstanceOf(NestedException.class);
        assertThat(errorCapture.getException()).isInstanceOf(CustomException.class);
    }

    @Test
    void testUnnestDoublyNestedException() {
        final NestedException nestedException = new NestedException(new NestedException(new CustomException()));
        ErrorCapture errorCapture = tracer.captureException(nestedException, null, null);
        assertThat(errorCapture).isNotNull();
        assertThat(errorCapture.getException()).isNotInstanceOf(NestedException.class);
        assertThat(errorCapture.getException()).isInstanceOf(CustomException.class);
    }

    @Test
    void testIgnoredNestedException() {
        doReturn(List.of(WildcardMatcher.valueOf("*CustomException"))).when(coreConfiguration).getIgnoreExceptions();
        final NestedException nestedException = new NestedException(new CustomException());
        ErrorCapture errorCapture = tracer.captureException(nestedException, null, null);
        assertThat(errorCapture).isNull();
    }

    @Test
    void testNonConfiguredNestingException() {
        final WrapperException wrapperException = new WrapperException(new CustomException());
        ErrorCapture errorCapture = tracer.captureException(wrapperException, null, null);
        assertThat(errorCapture).isNotNull();
        assertThat(errorCapture.getException()).isInstanceOf(WrapperException.class);
    }

    @Test
    void testNonConfiguredWrappingConfigured() {
        final NestedException nestedException = new NestedException(new WrapperException(new NestedException(new Exception())));
        ErrorCapture errorCapture = tracer.captureException(nestedException, null, null);
        assertThat(errorCapture).isNotNull();
        assertThat(errorCapture.getException()).isInstanceOf(WrapperException.class);
    }

    private static class NestedException extends Exception {
        public NestedException(Throwable cause) {
            super(cause);
        }
    }

    private static class WrapperException extends Exception {
        public WrapperException(Throwable cause) {
            super(cause);
        }
    }

    private static class CustomException extends Exception {}

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
