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
package co.elastic.apm.agent.jbosslogmanager.correlation;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.loginstr.correlation.AbstractLogCorrelationHelper;
import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogManager;
import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static co.elastic.apm.agent.impl.transaction.TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME;
import static org.assertj.core.api.Assertions.assertThat;

public class JBossLogManagerCorrelationInstrumentationTest extends AbstractInstrumentationTest {

    public static final String LOGGER_NAME = "Test-Logger";

    private static final LoggingCorrelationVerifier loggingCorrelationVerifier = new LoggingCorrelationVerifier();
    private static Logger logger;

    private Transaction transaction;

    @BeforeAll
    static void initializeLogger() {
        logger = (org.jboss.logmanager.Logger) LogManager.getLogManager().getLogger(LOGGER_NAME);
        logger.addHandler(new LoggingCorrelationVerifier());
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
    public void testSimple() {
        assertThat(loggingCorrelationVerifier.isVerified()).isFalse();
        // TraceContext#toString() returns the text representation of the traceparent header
        logger.info(transaction.getTraceContext().toString());
        assertThat(loggingCorrelationVerifier.isVerified()).isTrue();
    }

    // todo: enable once support for jboss-logmanager error logging is added
    @Test
    @Disabled
    public void testErrorLogging() {
        assertThat(loggingCorrelationVerifier.isVerified()).isFalse();
        // TraceContext#toString() returns the text representation of the traceparent header
        logger.log(Level.ERROR, transaction.getTraceContext().toString(), new RuntimeException());
        assertThat(loggingCorrelationVerifier.isVerified()).isTrue();
    }

    private static class LoggingCorrelationVerifier extends ExtHandler {
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
        protected void doPublish(ExtLogRecord record) {
            try {
                Object traceId = record.getMdc(AbstractLogCorrelationHelper.TRACE_ID_MDC_KEY);
                System.out.println("traceId = " + traceId);
                assertThat(traceId).isNotNull();
                Object transactionId = record.getMdc(AbstractLogCorrelationHelper.TRANSACTION_ID_MDC_KEY);
                System.out.println("transactionId = " + transactionId);
                assertThat(transactionId).isNotNull();
                Object errorId = record.getMdc(AbstractLogCorrelationHelper.ERROR_ID_MDC_KEY);
                System.out.println("errorId = " + transactionId);
                boolean shouldContainErrorId = record.getLevel().getName().equals("ERROR");
                String traceParent = record.getMessage();
                assertThat(traceParent).isNotNull();
                Map<String, String> textHeaderMap = Map.of(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, traceParent);
                TraceContext childTraceContext = TraceContext.with64BitId(tracer);
                TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(childTraceContext, textHeaderMap, TextHeaderMapAccessor.INSTANCE);
                System.out.println("childTraceContext = " + childTraceContext);
                assertThat(childTraceContext.getTraceId().toString()).isEqualTo(traceId.toString());
                assertThat(childTraceContext.getParentId().toString()).isEqualTo(transactionId.toString());
                if (shouldContainErrorId) {
                    assertThat(errorId).isNotNull();
                    ErrorCapture activeError = ErrorCapture.getActive();
                    assertThat(activeError).isNotNull();
                    assertThat(activeError.getTraceContext().getId().toString()).isEqualTo(errorId.toString());
                } else {
                    assertThat(errorId).isNull();
                }
                loggingCorrelationVerifier.setVerified();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }
}
