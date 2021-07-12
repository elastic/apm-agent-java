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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class LongListTest {

    private LongList longList;

    @BeforeEach
    void setUp() {
        longList = new LongList();
    }

    @Test
    void testAdd() {
        assertThat(longList.isEmpty()).isTrue();
        longList.add(42);
        assertThat(longList.isEmpty()).isFalse();
        assertThat(longList.getSize()).isEqualTo(1);
        assertThat(longList.get(0)).isEqualTo(42);
    }

    @Test
    void testContains() {
        longList.add(42);
        assertThat(longList.contains(42)).isTrue();
        assertThat(longList.contains(0)).isFalse();
    }

    @Test
    void testAddAll() {
        longList.add(42);
        LongList list2 = new LongList();
        list2.add(43);
        list2.add(44);
        longList.addAll(list2);
        assertThat(this.longList.getSize()).isEqualTo(3);
        assertThat(this.longList.get(0)).isEqualTo(42);
        assertThat(this.longList.get(1)).isEqualTo(43);
        assertThat(this.longList.get(2)).isEqualTo(44);
    }

    @Test
    void testAddAllLargeList() {
        longList.add(42);
        LongList list2 = new LongList();
        for (int i = 0; i < 42; i++) {
            list2.add(i);
        }
        longList.addAll(list2);
        assertThat(this.longList.getSize()).isEqualTo(43);
        assertThat(this.longList.get(0)).isEqualTo(42);
        assertThat(this.longList.get(1)).isEqualTo(0);
        assertThat(this.longList.get(2)).isEqualTo(1);
    }

    @Test
    void testNewCapacity() {
        assertThat(LongList.newCapacity(1, 0)).isEqualTo(1);
        assertThat(LongList.newCapacity(42, 4)).isEqualTo(42);
        assertThat(LongList.newCapacity(5, 4)).isEqualTo(6);
        assertThatThrownBy(() -> LongList.newCapacity(Integer.MAX_VALUE, 4)).isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    void testOutOfBounds() {
        assertThatThrownBy(() -> longList.get(0)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testGrow() {
        for (int i = 0; i < 42; i++) {
            longList.add(i);
        }

        assertThat(longList.getSize()).isEqualTo(42);

        for (int i = 0; i < 42; i++) {
            assertThat(longList.contains(i)).isTrue();
        }
    }

    @Test
    void testRemoveIndex() {
        longList.add(42);
        longList.add(43);
        longList.add(44);
        assertThat(longList.remove(1)).isEqualTo(43);

        assertThat(longList.getSize()).isEqualTo(2);
        assertThat(this.longList.get(0)).isEqualTo(42);
        assertThat(this.longList.get(1)).isEqualTo(44);
    }

    @Test
    void testRemoveIndexOutOfBounds() {
        longList.add(42);
        assertThatThrownBy(() -> longList.remove(1)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testRemoveLongLast() {
        longList.add(42);
        longList.add(43);
        longList.add(44);
        assertThat(longList.remove(44L)).isTrue();

        assertThat(longList.getSize()).isEqualTo(2);
        assertThat(this.longList.get(0)).isEqualTo(42);
        assertThat(this.longList.get(1)).isEqualTo(43);
    }

    @Test
    void testRemoveNotInList() {
        longList.add(42);
        assertThat(longList.remove(44L)).isFalse();

        assertThat(longList.getSize()).isEqualTo(1);
        assertThat(this.longList.get(0)).isEqualTo(42);
    }

    @Test
    void testRemoveLong() {
        longList.add(42);
        longList.add(43);
        longList.add(44);
        longList.remove(42L);

        assertThat(longList.getSize()).isEqualTo(2);
        assertThat(this.longList.get(0)).isEqualTo(43);
        assertThat(this.longList.get(1)).isEqualTo(44);
    }
}
