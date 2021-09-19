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
package co.elastic.apm.agent.collections;

import co.elastic.apm.agent.sdk.weakmap.WeakMaps;
import co.elastic.apm.agent.sdk.weakmap.WeakSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NullSafeWeakConcurrentSetTest {

    private WeakSet<String> set;

    @BeforeEach
    void init() {
        set = WeakMaps.createSet();
    }

    @Test
    void nullValuesShouldNotThrow() {
        assertThat(set.add(null)).isFalse();
        assertThat(set.remove(null)).isFalse();
        assertThat(set.contains(null)).isFalse();
    }
}
