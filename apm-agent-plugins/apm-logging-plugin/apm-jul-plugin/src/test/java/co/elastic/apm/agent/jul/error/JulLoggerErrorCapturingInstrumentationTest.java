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
package co.elastic.apm.agent.jul.error;

import co.elastic.apm.agent.loginstr.error.AbstractErrorLoggingInstrumentationTest;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class JulLoggerErrorCapturingInstrumentationTest extends AbstractErrorLoggingInstrumentationTest {

    private static final Logger logger = Logger.getLogger(JulLoggerErrorCapturingInstrumentationTest.class.getName());

    @Test
    void captureErrorExceptionWithStringMessage() {
        logWithMessage(Level.SEVERE);
        verifyExceptionCaptured("some business exception", RuntimeException.class);
    }

    @Test
    void ignoreWarningWithStringMessage() {
        logWithMessage(Level.WARNING);
        verifyNoExceptionCaptured();
    }

    private static void logWithMessage(Level level) {
        logger.log(level, "exception captured", new RuntimeException("some business exception"));
    }

    @Test
    void captureErrorExceptionWithLogRecord() {
        logWithLogRecord(Level.SEVERE);
        verifyExceptionCaptured("some business exception", RuntimeException.class);
    }

    @Test
    void ignoreWarningWithLogRecord() {
        logWithLogRecord(Level.WARNING);
        verifyNoExceptionCaptured();
    }

    private static void logWithLogRecord(Level warning) {
        LogRecord lr = new LogRecord(warning, "exception captured");
        lr.setThrown(new RuntimeException("some business exception"));
        logger.log(lr);
    }
}
