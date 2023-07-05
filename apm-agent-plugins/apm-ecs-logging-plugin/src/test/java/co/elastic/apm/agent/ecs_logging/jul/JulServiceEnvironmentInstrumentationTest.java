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

import co.elastic.apm.agent.ecs_logging.EcsServiceEnvironmentTest;
import co.elastic.apm.agent.ecs_logging.EcsServiceNameTest;
import co.elastic.apm.agent.testutils.TestClassWithDependencyRunner;
import co.elastic.logging.jul.EcsFormatter;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

// must be tested in isolation, either used in agent or has side effects
@TestClassWithDependencyRunner.DisableOutsideOfRunner
public class JulServiceEnvironmentInstrumentationTest extends EcsServiceEnvironmentTest {

    private EcsFormatter formatter;

    @Override
    protected String createLogMsg() {
        return formatter.format(new LogRecord(Level.INFO, "msg"));
    }

    @Override
    protected void initFormatterWithoutServiceEnvironmentSet() {
        formatter = createFormatter(Collections.emptyMap());
    }

    @Override
    protected void initFormatterWithServiceEnvironment(String environment) {
        formatter = createFormatter(Map.of("co.elastic.logging.jul.EcsFormatter.serviceEnvironment", environment));
    }

    private static EcsFormatter createFormatter(Map<String, String> map) {
        try {
            LogManagerTestInstrumentation.JulProperties.override(map);
            return new EcsFormatter();
        } finally {
            LogManagerTestInstrumentation.JulProperties.restore();
        }
    }

}
