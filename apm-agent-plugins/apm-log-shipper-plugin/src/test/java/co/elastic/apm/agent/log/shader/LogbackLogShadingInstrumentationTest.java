/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.log.shader;

import ch.qos.logback.classic.Logger;
import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.mockito.Mockito.when;

class LogbackLogShadingInstrumentationTest extends AbstractInstrumentationTest {

    private Logger logbackLogger;

    @BeforeEach
    void setup() {
        when(config.getConfig(CoreConfiguration.class).getServiceName()).thenReturn("LogbackTest");
        logbackLogger = (Logger) LoggerFactory.getLogger("Test-File-Logger");
    }

    @Test
    void testSimpleLogShading() {
        MDC.put("trace.id", "afiuawrwuehrwu");
        logbackLogger.trace("Trace");
        logbackLogger.debug("Debug");
        logbackLogger.warn("Warn");
        logbackLogger.error("Error");
    }

    // Disabled - very slow. Can be used for file rolling manual testing
    // @Test
    void testShadeLogRolling() {
        when(config.getConfig(LoggingConfiguration.class).getLogFileMaxSize()).thenReturn(100L);
        logbackLogger.trace("First line");
        sleep();
        logbackLogger.debug("Second Line");
        sleep();
        logbackLogger.trace("Third line");
        sleep();
        logbackLogger.debug("Fourth line");
        sleep();
    }

    private void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
