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
package co.elastic.apm.agent.springai;

import co.elastic.apm.agent.testutils.TestClassWithDependencyRunner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

class SpringAiVersionIT {

    @ParameterizedTest
    @ValueSource(strings = {"1.0.8", "1.1.7"})
    void rejectsUnsupportedSpringAiVersions(String version) throws Exception {
        runVersionTest(version, SpringAiUnsupportedVersionTest.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.0.0", "2.0.2"})
    void acceptsSupportedSpringAiVersions(String version) throws Exception {
        runVersionTest(version, SpringAiSupportedVersionTest.class);
    }

    private static void runVersionTest(String version, Class<?> testClass) throws Exception {
        List<String> dependencies = List.of(
            "org.springframework.ai:spring-ai-model:" + version,
            "org.springframework.ai:spring-ai-commons:" + version,
            "org.springframework.ai:spring-ai-vector-store:" + version);
        TestClassWithDependencyRunner runner = new TestClassWithDependencyRunner(
            dependencies, testClass.getName());
        runner.run();
    }
}
