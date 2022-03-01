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
package co.elastic.apm.agent.slf4j.error;

import co.elastic.apm.agent.logging.error.AbstractErrorLoggingInstrumentationTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Slf4jLoggerErrorCapturingInstrumentationTest extends AbstractErrorLoggingInstrumentationTest {

    private static final Logger logger = LoggerFactory.getLogger(Slf4jLoggerErrorCapturingInstrumentationTest.class);

    @Test
    void captureException() {
        logger.error("exception captured", new RuntimeException("some business exception"));
        verifyThatExceptionCaptured(1, "some business exception", RuntimeException.class);
    }

}
