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
package co.elastic.apm.agent.testutils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testing the maven-surefire-plugin {@code rerunFailingTestsCount} feature.
 * This test is expected to fail in the first attempt and succeed in the second.
 * It would therefore be considered as successful and the surefire output xml log should contain the relevant {@code flakyFailure}.
 */
public class FlakyTest {

    public static final String TEST_RUN_SYSTEM_PROPERTY_KEY = "co.elastic.agent.flaky.test.mark";
    @Test
    void passOnSecondRun() {
        try {
            assertThat(System.getProperty(TEST_RUN_SYSTEM_PROPERTY_KEY))
                .describedAs("This test is expected to fail in its first run")
                .isNotNull();
        } finally {
            System.setProperty(TEST_RUN_SYSTEM_PROPERTY_KEY, "ran");
        }
    }
}
