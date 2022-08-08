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
package co.elastic.apm.agent.ecs_logging;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.logging.jul.EcsFormatter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

public class JulEcsFormatterInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testNoCorrelation() {
        JsonNode logLine = logSomething();

        assertThat(logLine.get("transaction.id")).isNull();
        assertThat(logLine.get("trace.id")).isNull();
    }

    @Test
    void testActiveTransaction() {
        Transaction transaction = startTestRootTransaction("log");
        try {
            JsonNode logLine = logSomething();

            assertThat(logLine.get("transaction.id").textValue()).isEqualTo(transaction.getTraceContext().getTransactionId().toString());
            assertThat(logLine.get("trace.id").textValue()).isEqualTo(transaction.getTraceContext().getTraceId().toString());
        } finally {
            transaction.deactivate().end();
        }
    }

    @Test
    void testActiveError() {
        ErrorCapture error = new ErrorCapture(tracer);

        error.activate();
        try {
            JsonNode logLine = logSomething();

            assertThat(logLine.get("error.id").textValue()).isEqualTo(error.getTraceContext().getId().toString());
        } finally {
            error.deactivate();
        }

    }

    private static JsonNode logSomething() {
        EcsFormatter formatter = new EcsFormatter();
        LogRecord record = new LogRecord(Level.INFO, "msg");
        try {
            return new ObjectMapper().readTree(formatter.format(record));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
