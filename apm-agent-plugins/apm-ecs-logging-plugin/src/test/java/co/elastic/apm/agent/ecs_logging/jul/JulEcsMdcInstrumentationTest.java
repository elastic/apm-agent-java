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
package co.elastic.apm.agent.ecs_logging.jul;

import co.elastic.apm.agent.ecs_logging.EcsLoggingTest;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.logging.jul.EcsFormatter;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

public class JulEcsMdcInstrumentationTest extends EcsLoggingTest {

    private EcsFormatter formatter = new EcsFormatter();

    @Test
    void testNoCorrelation() {
        String json = createLogMsg();
        assertThat(getJson(json, "transaction.id")).isNull();
        assertThat(getJson(json, "trace.id")).isNull();
    }

    @Test
    void testActiveTransaction() {
        Transaction transaction = startTestRootTransaction("log");
        try {
            String json = createLogMsg();
            assertThat(getJson(json, "transaction.id")).isEqualTo(transaction.getTraceContext().getTransactionId().toString());
            assertThat(getJson(json, "trace.id")).isEqualTo(transaction.getTraceContext().getTraceId().toString());
        } finally {
            transaction.deactivate().end();
        }
    }

    @Test
    void testActiveError() {
        ErrorCapture error = new ErrorCapture(tracer);

        error.activate();
        try {
            String json = createLogMsg();
            assertThat(getJson(json, "error.id")).isEqualTo(error.getTraceContext().getId().toString());
        } finally {
            error.deactivate();
        }
    }

    @Override
    protected String createLogMsg() {
        return formatter.format(new LogRecord(Level.INFO, "msg"));
    }

}
