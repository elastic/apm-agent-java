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
package co.elastic.apm.agent.log4j2;

import co.elastic.apm.agent.testutils.TestClassWithDependencyRunner;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

@DisabledOnOs(OS.WINDOWS)
public class Log4j2InstrumentationTest {

    @ParameterizedTest
    @ValueSource(strings = {"2.6", "2.17.1", "2.24.0"})
    public void testInstrumentation(String version) throws Exception {
        List<String> dependencies = List.of(
            "org.apache.logging.log4j:log4j-core:" + version,
            "org.apache.logging.log4j:log4j-api:" + version,
            "co.elastic.logging:log4j2-ecs-layout:1.3.2"
        );
        new TestClassWithDependencyRunner(dependencies, Log4j2InstrumentationTestDefinitions.class.getName(),
            Log4j2InstrumentationTestDefinitions.Log4j2LoggerFacade.class.getName())
            .run();
    }

    //TODO: Error capturing is currently broken on 2.6
    @ParameterizedTest
    @ValueSource(strings = {/*"2.6",*/ "2.17.1", "2.24.0"})
    public void testErrorCapture(String version) throws Exception {
        List<String> dependencies = List.of(
            "org.apache.logging.log4j:log4j-core:" + version,
            "org.apache.logging.log4j:log4j-api:" + version
        );
        new TestClassWithDependencyRunner(dependencies, Log4j2LoggerErrorCapturingInstrumentationTest.class.getName())
            .run();
    }
}
