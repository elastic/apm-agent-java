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
        longList.add(42);
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
}
