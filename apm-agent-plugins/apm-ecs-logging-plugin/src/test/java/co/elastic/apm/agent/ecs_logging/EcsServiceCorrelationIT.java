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
package co.elastic.apm.agent.ecs_logging;

import co.elastic.apm.agent.testutils.TestClassWithDependencyRunner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

public abstract class EcsServiceCorrelationIT {

    protected abstract String getArtifactName();

    protected abstract String getServiceNameTestClass();

    protected abstract String getServiceVersionTestClass();

    protected abstract String getServiceEnvironmentTestClass();

    @ParameterizedTest(name = "ecs-logging {0}, supports version = {1}, supports environment = {2}")
    @CsvSource(delimiter = '|', value = {
        "1.3.2 | false | false", // 1.3.2 only supports service name
        "1.4.0 | true  | false", // 1.4.0 adds service version
        "1.5.0 | true  | true" // 1.5.0 adds service environment
    })
    void testVersion(String version, boolean serviceVersionSupported, boolean serviceEnvironmentSupported) throws Exception {
        String dependency = String.format("co.elastic.logging:%s:%s", getArtifactName(), version);
        new TestClassWithDependencyRunner(List.of(dependency), getServiceNameTestClass()).run();

        if (serviceVersionSupported) {
            new TestClassWithDependencyRunner(List.of(dependency), getServiceVersionTestClass()).run();
        }

        if (serviceEnvironmentSupported) {
            new TestClassWithDependencyRunner(List.of(dependency), getServiceEnvironmentTestClass()).run();
        }
    }
}
