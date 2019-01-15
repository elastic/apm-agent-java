/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class PotentiallyMultiValuedMapTest {

    private PotentiallyMultiValuedMap map;

    @BeforeEach
    void setUp() {
        map = new PotentiallyMultiValuedMap();
    }

    @Test
    void testMap_zeroEntries() {
        assertThat(map.get("foo")).isNull();
        assertThat(map.getFirst("foo")).isNull();
        assertThat(map.getAll("foo")).isEmpty();
    }

    @Test
    void testMap_oneEntry() {
        map.add("foo", "bar");
        assertThat(map.get("foo")).isEqualTo("bar");
        assertThat(map.getFirst("foo")).isEqualTo("bar");
        assertThat(map.getAll("foo")).isEqualTo(Collections.singletonList("bar"));
    }

    @Test
    void testMap_twoEntries() {
        map.add("foo", "bar");
        map.add("foo", "baz");
        assertThat(map.get("foo")).isEqualTo(Arrays.asList("bar", "baz"));
        assertThat(map.getFirst("foo")).isEqualTo("bar");
        assertThat(map.getAll("foo")).isEqualTo(Arrays.asList("bar", "baz"));
    }

    @Test
    void testRemove() {
        map.add("foo", "bar");
        map.removeIgnoreCase("Foo");
        assertThat(map.size()).isZero();
    }

    @Test
    void testContains() {
        map.add("foo", "bar");
        assertThat(map.containsIgnoreCase("Foo")).isTrue();
        assertThat(map.containsIgnoreCase("bar")).isFalse();
    }

    @Test
    void testSet() {
        map.add("foo", "bar");
        map.set(0, "foo");
        assertThat(map.get("foo")).isEqualTo("foo");
    }

    @Test
    void testSetArray() {
        map.set("foo", new String[]{"bar"});
        assertThat(map.get("foo")).isEqualTo("bar");
    }

    @Test
    void testSetArrayTwoElements() {
        map.set("foo", new String[]{"bar", "baz"});
        assertThat(map.get("foo")).isEqualTo(Arrays.asList("bar", "baz"));
    }

}
