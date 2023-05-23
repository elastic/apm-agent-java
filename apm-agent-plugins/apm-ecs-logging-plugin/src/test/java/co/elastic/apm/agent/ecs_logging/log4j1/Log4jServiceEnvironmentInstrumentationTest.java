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
package co.elastic.apm.agent.ecs_logging.log4j1;

import co.elastic.apm.agent.ecs_logging.EcsServiceEnvironmentTest;
import co.elastic.apm.agent.testutils.TestClassWithDependencyRunner;
import co.elastic.logging.log4j.EcsLayout;
import org.apache.log4j.Category;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.RootLogger;

// must be tested in isolation, either used in agent or has side effects
@TestClassWithDependencyRunner.DisableOutsideOfRunner
public class Log4jServiceEnvironmentInstrumentationTest extends EcsServiceEnvironmentTest {

    private EcsLayout ecsLayout;

    @Override
    protected void initFormatterWithoutServiceEnvironmentSet() {
        ecsLayout = new EcsLayout();
    }

    @Override
    protected void initFormatterWithServiceEnvironment(String environment) {
        ecsLayout = new EcsLayout();
        ecsLayout.setServiceEnvironment(environment);
    }

    @Override
    protected String createLogMsg() {
        Category logger = new RootLogger(Level.ALL);
        LoggingEvent event = new LoggingEvent("", logger, System.currentTimeMillis(), Level.INFO, "msg", null);
        return ecsLayout.format(event);
    }

}
