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
package co.elastic.apm.agent.sdk.weakmap;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

class NullSafeWeakConcurrentMapTest {

    private WeakConcurrentMap<String, String> map;

    @BeforeEach
    void init() {
        map = new NullSafeWeakConcurrentMap<String, String>(false) {
            @Override
            protected String defaultValue(String key) {
                return "default-" + key;
            }
        };
    }

    @Test
    void nullKeyShouldNotThrow() {
        doesNotContainKey(null);

        // no entry should be added to the map due to side-effects of 'get'
        mapIsEmpty();
    }

    @Test
    void returnNonNullKey() {
        String key = "key";
        String value = "value";

        doesNotContainKey(key);

        map.put(key, value);

        assertThat(map.containsKey(key)).isTrue();
        assertThat(map.get(key)).isEqualTo(value);
        assertThat(map.getIfPresent(key)).isEqualTo(value);

        assertThat(map.containsKey(key)).isTrue();

        assertThat(map.put(key, value)).isEqualTo(value);
        assertThat(map.putIfAbsent(key, value)).isEqualTo(value);
        assertThat(map.putIfProbablyAbsent(key, value)).isEqualTo(value);

        assertThat(map.remove(key)).isEqualTo(value);

        // key has been removed
        doesNotContainKey(key);

    }

    @Test
    void putNullValues() {
        String key = "key";

        assertThat(map.put(key, null)).isNull();
        doesNotContainKey(key);
        mapIsEmpty();

        assertThat(map.putIfAbsent(key, null)).isNull();
        doesNotContainKey(key);
        mapIsEmpty();

        assertThat(map.putIfProbablyAbsent(key, null)).isNull();
        doesNotContainKey(key);
        mapIsEmpty();
    }

    private void doesNotContainKey(@Nullable String key) {
        assertThat(map.getIfPresent(key)).isNull();

        assertThat(map.containsKey(key)).isFalse();
        assertThat(map.remove(key)).isNull();

        if (key != null) {
            // get also adds a map entry with default value
            String defaultValue = "default-" + key;
            assertThat(map.get(key)).isEqualTo(defaultValue);
            assertThat(map.containsKey(key))
                .describedAs("get with non-null key adds default value")
                .isTrue();

            assertThat(map.remove(key)).isEqualTo(defaultValue);
            assertThat(map.containsKey(key)).isFalse();
        } else {
            assertThat(map.get(key)).isNull();
            assertThat(map.containsKey(key)).isFalse();
        }

    }

    private void mapIsEmpty() {
        assertThat(map.approximateSize()).isEqualTo(0);
        assertThat(map.iterator()).isExhausted();
    }

}
