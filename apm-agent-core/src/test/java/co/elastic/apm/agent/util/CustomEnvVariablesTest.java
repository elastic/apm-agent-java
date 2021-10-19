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
package co.elastic.apm.agent.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomEnvVariablesTest extends CustomEnvVariables {

    @Test
    void testCustomSingleEnvVariable() {
        String pathVariable = "PATH";
        final String originalPath = System.getenv(pathVariable);
        String mockPath = "mock/path";
        final Map<String, String> customVariables = Map.of("key1", "value1", pathVariable, mockPath);
        runWithCustomEnvVariables(customVariables, () -> {
            String returnedPath = System.getenv(pathVariable);
            assertThat(returnedPath).isEqualTo(mockPath);
        });
        String returnedPath = System.getenv(pathVariable);
        assertThat(returnedPath).isEqualTo(originalPath);
    }

    @Test
    void testSingleEnvVariables() {
        final Map<String, String> originalVariables = System.getenv();
        final Map<String, String> customVariables = Map.of("key1", "value1", "key2", "value2");
        runWithCustomEnvVariables(customVariables, () -> {
            Map<String, String> returnedEnvVariables = System.getenv();
            assertThat(returnedEnvVariables).containsAllEntriesOf(originalVariables);
            assertThat(returnedEnvVariables).containsAllEntriesOf(customVariables);
        });
        Map<String, String> returnedEnvVariables = System.getenv();
        assertThat(returnedEnvVariables).containsAllEntriesOf(originalVariables);
        customVariables.forEach((key, value) -> assertThat(returnedEnvVariables).doesNotContainEntry(key, value));
    }
}
