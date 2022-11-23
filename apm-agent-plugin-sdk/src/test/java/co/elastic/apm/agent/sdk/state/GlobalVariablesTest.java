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

import org.junit.jupiter.api.Test;
import test.other.pkg.OtherClass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalVariablesTest {

    @Test
    void simple() {
        String s = GlobalVariables.get(GlobalVariablesTest.class, "hello", "world");
        assertThat(s).isEqualTo("world"); // should get and store default
        assertThat(GlobalVariables.get(GlobalVariablesTest.class, "hello", "other")).isEqualTo("world");
    }

    @Test
    void tryInvalidClass() {
        // using a default value whose CL is not the bootstrap CL and is not part of the instrumentation plugin
        // should be avoided as it might introduce classloader leak, for example when an instrumented library class
        // is used.
        assertThatThrownBy(() -> {
            GlobalVariables.get(OtherClass.class, "hello", new OtherClass());
        }).isInstanceOf(IllegalArgumentException.class);

    }

}
