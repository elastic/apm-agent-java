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
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.RequestImpl;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.error.RedactedException;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfigurationImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

class ErrorCaptureTest {

    private ElasticApmTracer tracer;
    private StacktraceConfigurationImpl stacktraceConfiguration;
    private CoreConfigurationImpl coreConfiguration;

    @BeforeEach
    void setUp() {
        tracer = MockTracer.createRealTracer();
        stacktraceConfiguration = tracer.getConfig(StacktraceConfigurationImpl.class);
        coreConfiguration = tracer.getConfig(CoreConfigurationImpl.class);
    }

    @Test
    void testCulpritApplicationPackagesNotConfigured() {
        final ErrorCaptureImpl errorCapture = new ErrorCaptureImpl(tracer);
        errorCapture.setException(new Exception());
        assertThat(errorCapture.getCulprit()).isEmpty();
    }

    @Test
    void testCulprit() {
        doReturn(List.of("org.example.stacktrace")).when(stacktraceConfiguration).getApplicationPackages();
        final ErrorCaptureImpl errorCapture = new ErrorCaptureImpl(tracer);
        final Exception nestedException = new Exception();
        final Exception topLevelException = new Exception(nestedException);
        errorCapture.setException(topLevelException);
        assertThat(errorCapture.getCulprit()).startsWith("org.example.stacktrace.ErrorCaptureTest.testCulprit(ErrorCaptureTest.java:");
        assertThat(errorCapture.getCulprit()).endsWith(":" + nestedException.getStackTrace()[0].getLineNumber() + ")");
    }

    @Test
    void testUnnestNestedException() {
        final NestedException nestedException = new NestedException(new CustomException());
        ErrorCaptureImpl errorCapture = tracer.captureException(nestedException, tracer.currentContext(), null);
        assertThat(errorCapture).isNotNull();
        assertThat(errorCapture.getException()).isNotInstanceOf(NestedException.class);
        assertThat(errorCapture.getException()).isInstanceOf(CustomException.class);
    }

    @Test
    void testUnnestDoublyNestedException() {
        final NestedException nestedException = new NestedException(new NestedException(new CustomException()));
        ErrorCaptureImpl errorCapture = tracer.captureException(nestedException, tracer.currentContext(), null);
        assertThat(errorCapture).isNotNull();
        assertThat(errorCapture.getException()).isNotInstanceOf(NestedException.class);
        assertThat(errorCapture.getException()).isInstanceOf(CustomException.class);
    }

    @Test
    void testIgnoredNestedException() {
        doReturn(List.of(WildcardMatcher.valueOf("*CustomException"))).when(coreConfiguration).getIgnoreExceptions();
        final NestedException nestedException = new NestedException(new CustomException());
        ErrorCaptureImpl errorCapture = tracer.captureException(nestedException, tracer.currentContext(), null);
        assertThat(errorCapture).isNull();
    }

    @Test
    void testNonConfiguredNestingException() {
        final WrapperException wrapperException = new WrapperException(new CustomException());
        ErrorCaptureImpl errorCapture = tracer.captureException(wrapperException, tracer.currentContext(), null);
        assertThat(errorCapture).isNotNull();
        assertThat(errorCapture.getException()).isInstanceOf(WrapperException.class);
    }

    @Test
    void testNonConfiguredWrappingConfigured() {
        final NestedException nestedException = new NestedException(new WrapperException(new NestedException(new Exception())));
        ErrorCaptureImpl errorCapture = tracer.captureException(nestedException, tracer.currentContext(), null);
        assertThat(errorCapture).isNotNull();
        assertThat(errorCapture.getException()).isInstanceOf(WrapperException.class);
    }

    @Test
    void testExceptionRedaction() {
        doReturn(true).when(coreConfiguration).isRedactExceptions();
        assertThat(tracer.redactExceptionIfRequired(null)).isNull();

        Exception exception = new CustomException();
        Throwable redacted = tracer.redactExceptionIfRequired(exception);
        assertThat(redacted).isInstanceOf(RedactedException.class);

        // double redaction means no instanceof check
        assertThat(tracer.redactExceptionIfRequired(redacted)).isNotSameAs(redacted);

        ErrorCaptureImpl errorCapture = tracer.captureException(exception, tracer.currentContext(), null);
        assertThat(errorCapture).isNotNull();
        assertThat(errorCapture.getException()).isInstanceOf(RedactedException.class);

        ErrorCaptureImpl alreadyRedacted = tracer.captureException(redacted, tracer.currentContext(), null);
        assertThat(alreadyRedacted).isNotNull();
        assertThat(alreadyRedacted.getException()).isNotSameAs(redacted);
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
        final TransactionImpl transaction = new TransactionImpl(tracer);
        RequestImpl transactionRequest = transaction.getContext().getRequest()
            .withMethod("GET")
            .addHeader("key", "value");
        transactionRequest.withBodyBuffer().append("TEST");
        transactionRequest.endOfBufferInput();
        final ErrorCaptureImpl errorCapture = new ErrorCaptureImpl(tracer).asChildOf(transaction);
        RequestImpl errorRequest = errorCapture.getContext().getRequest();
        assertThat(errorRequest.getMethod()).isEqualTo("GET");
        assertThat(errorRequest.getHeaders().get("key")).isEqualTo("value");
        assertThat(errorRequest.getBodyBufferForSerialization()).isNotNull();
        assertThat(errorRequest.getBodyBufferForSerialization().toString()).isEqualTo("TEST");
    }

    @Test
    void testTransactionContextTransferNonFinishedBody() {
        final TransactionImpl transaction = new TransactionImpl(tracer);
        RequestImpl transactionRequest = transaction.getContext().getRequest()
            .withMethod("GET")
            .addHeader("key", "value");
        transactionRequest.withBodyBuffer().append("TEST");
        final ErrorCaptureImpl errorCapture = new ErrorCaptureImpl(tracer).asChildOf(transaction);
        RequestImpl errorRequest = errorCapture.getContext().getRequest();
        assertThat(errorRequest.getMethod()).isEqualTo("GET");
        assertThat(errorRequest.getHeaders().get("key")).isEqualTo("value");
        assertThat(errorRequest.getBodyBufferForSerialization())
            .describedAs("Body buffer should be null when copying from a transaction where capturing was not finished yet")
            .isNull();
    }

    @Test
    void testActiveError() {
        assertThat(ErrorCaptureImpl.getActive()).isNull();
        ErrorCaptureImpl errorCapture = new ErrorCaptureImpl(tracer).activate();
        assertThat(ErrorCaptureImpl.getActive()).isNotNull();
        errorCapture.deactivate().end();
        assertThat(ErrorCaptureImpl.getActive()).isNull();
    }


}
