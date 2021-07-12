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
package co.elastic.apm.agent.sdk.state;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallDepthTest {

    private CallDepth callDepth;

    @BeforeEach
    void setUp() {
        callDepth = CallDepth.get(CallDepthTest.class);
    }

    @AfterEach
    void tearDown() {
        CallDepth.clearRegistry();
    }

    @Test
    void testDetectNestedCalls() {
        assertThat(callDepth.isNestedCallAndIncrement()).isFalse();
        assertThat(callDepth.isNestedCallAndIncrement()).isTrue();
        assertThat(callDepth.isNestedCallAndDecrement()).isTrue();
        assertThat(callDepth.isNestedCallAndDecrement()).isFalse();
    }

    @Test
    void testNegativeCount() {
        assertThatThrownBy(() -> callDepth.decrement()).isInstanceOf(AssertionError.class);
    }
}
