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
package co.elastic.apm.agent.log4j2.error;

import co.elastic.apm.agent.loginstr.error.AbstractErrorLoggingInstrumentationTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Only tested through dedicated class loaders for latest and oldest-supported versions.
 * See {@link Log4j2ErrorCapturingTestVersions}
 */
@Disabled
public class Log4j2LoggerErrorCapturingInstrumentationTest extends AbstractErrorLoggingInstrumentationTest {

    private static final Logger logger = LogManager.getLogger(Log4j2LoggerErrorCapturingInstrumentationTest.class);

    @Test
    void captureErrorExceptionWithStringMessage() {
        logger.error("exception captured", new RuntimeException("some business exception"));
        verifyThatExceptionCaptured(1, "some business exception", RuntimeException.class);
    }

    @Test
    void captureErrorExceptionWithMessageMessage() {
        logger.error(ParameterizedMessageFactory.INSTANCE.newMessage("exception captured with parameter {}", "foo"), new RuntimeException("some business exception"));
        verifyThatExceptionCaptured(1, "some business exception", RuntimeException.class);
    }

    @Test
    void captureFatalException() {
        logger.fatal("exception captured", new RuntimeException("some business exception"));
        verifyThatExceptionCaptured(1, "some business exception", RuntimeException.class);
    }
}
