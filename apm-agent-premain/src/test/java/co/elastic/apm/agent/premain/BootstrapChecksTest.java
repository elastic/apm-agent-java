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
package co.elastic.apm.agent.premain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapChecksTest {

    @Test
    void testBootstrapChecks() {
        assertThat(new BootstrapChecks(true, errors -> errors.addError("Fail")).isPassing()).isFalse();
        assertThat(new BootstrapChecks(false, errors -> errors.addError("Fail")).isPassing()).isTrue();
        assertThat(new BootstrapChecks(true, errors -> {}).isPassing()).isTrue();
        assertThat(new BootstrapChecks(false, errors -> {}).isPassing()).isTrue();
    }
}
