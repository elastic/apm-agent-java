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

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

public class LegacyLog4j2ShadingTestRunner {
    private final TestClassWithDependencyRunner runner;

    public LegacyLog4j2ShadingTestRunner() throws Exception {
        List<String> dependencies = List.of(
            "org.apache.logging.log4j:log4j-core:2.6",
            "org.apache.logging.log4j:log4j-api:2.6",
            "co.elastic.logging:log4j2-ecs-layout:1.3.2"
        );
        runner = new TestClassWithDependencyRunner(dependencies, LegacyLog4j2ShadingTest.class, Log4j2ShadingTest.class,
            Log4j2ShadingTest.Log4j2LoggerFacade.class);
    }

    @Test
    public void testVersions() {
        runner.run();
    }
}
