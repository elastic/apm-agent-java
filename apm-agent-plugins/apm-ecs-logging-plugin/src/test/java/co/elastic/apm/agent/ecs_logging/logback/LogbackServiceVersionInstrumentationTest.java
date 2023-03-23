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
package co.elastic.apm.agent.ecs_logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import co.elastic.apm.agent.ecs_logging.EcsServiceVersionTest;
import co.elastic.apm.agent.testutils.TestClassWithDependencyRunner;
import co.elastic.logging.logback.EcsEncoder;

// must be tested in isolation, either used in agent or has side effects
@TestClassWithDependencyRunner.DisableOutsideOfRunner
public class LogbackServiceVersionInstrumentationTest extends EcsServiceVersionTest {

    private EcsEncoder ecsEncoder;

    @Override
    protected String createLogMsg() {
        LoggerContext loggerContext = new LoggerContext();
        Logger logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        ILoggingEvent event = new LoggingEvent("co.elastic.apm.agent.ecs_logging.logback.LogbackServiceNameInstrumentationTest", logger, Level.INFO, "msg", null, null);
        return new String(ecsEncoder.encode(event));
    }

    @Override
    protected void initFormatterWithoutServiceVersionSet() {
        ecsEncoder = new EcsEncoder();
        ecsEncoder.start();
    }

    @Override
    protected void initFormatterWithServiceVersion(String version) {
        ecsEncoder = new EcsEncoder();
        ecsEncoder.start();
        ecsEncoder.setServiceVersion(version);
    }
}
