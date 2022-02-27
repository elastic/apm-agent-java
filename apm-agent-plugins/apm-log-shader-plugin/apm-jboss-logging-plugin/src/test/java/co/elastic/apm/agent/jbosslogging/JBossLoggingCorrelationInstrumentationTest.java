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
package co.elastic.apm.agent.jbosslogging;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.log.shader.AbstractLogCorrelationHelper;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static co.elastic.apm.agent.impl.transaction.TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME;
import static org.assertj.core.api.Assertions.assertThat;

public class JBossLoggingCorrelationInstrumentationTest extends AbstractInstrumentationTest {

    public static final String LOGGER_NAME = "Test-Logger";

    private static final LoggingCorrelationVerifier loggingCorrelationVerifier = new LoggingCorrelationVerifier();
    private static Logger logger;

    private Transaction transaction;

    @BeforeAll
    static void initializeLogger() {
        System.setProperty("org.jboss.logging.provider", "jdk");
        logger = Logger.getLogger(LOGGER_NAME);

        // Getting the underlying JULI logger in order to set a handler
        java.util.logging.Logger juliLogger = java.util.logging.Logger.getLogger(LOGGER_NAME);
        assertThat(juliLogger).isNotNull();
        juliLogger.addHandler(new LoggingCorrelationVerifier());
    }

    @BeforeEach
    public void setup() {
        transaction = Objects.requireNonNull(tracer.startRootTransaction(null)).activate();
        loggingCorrelationVerifier.reset();
    }

    @AfterEach
    public void tearDown() {
        transaction.deactivate().end();
    }

    @Test
    public void testJdkLogger() {
        assertThat(loggingCorrelationVerifier.isVerified()).isFalse();
        // TraceContext#toString() returns the text representation of the traceparent header
        logger.info(transaction.getTraceContext());
        assertThat(loggingCorrelationVerifier.isVerified()).isTrue();
    }

    private static class LoggingCorrelationVerifier extends Handler {
        private boolean verified;

        public void reset() {
            verified = false;
        }

        public boolean isVerified() {
            return verified;
        }

        public void setVerified() {
            verified = true;
        }

        @Override
        public void publish(LogRecord record) {
            try {
                Object traceId = MDC.get(AbstractLogCorrelationHelper.TRACE_ID_MDC_KEY);
                assertThat(traceId).isNotNull();
                System.out.println("traceId = " + traceId);
                Object transactionId = MDC.get(AbstractLogCorrelationHelper.TRANSACTION_ID_MDC_KEY);
                assertThat(transactionId).isNotNull();
                System.out.println("transactionId = " + transactionId);
                String traceParent = record.getMessage();
                assertThat(traceParent).isNotNull();
                Map<String, String> textHeaderMap = Map.of(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, traceParent);
                TraceContext childTraceContext = TraceContext.with64BitId(tracer);
                TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(childTraceContext, textHeaderMap, TextHeaderMapAccessor.INSTANCE);
                System.out.println("childTraceContext = " + childTraceContext);
                assertThat(childTraceContext.getTraceId().toString()).isEqualTo(traceId.toString());
                assertThat(childTraceContext.getParentId().toString()).isEqualTo(transactionId.toString());
                loggingCorrelationVerifier.setVerified();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {

        }
    }
}
