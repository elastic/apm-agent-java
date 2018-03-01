package co.elastic.apm.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class PotentiallyMultiValuedMapTest {

    private PotentiallyMultiValuedMap<String, String> map;

    @BeforeEach
    void setUp() {
        map = new PotentiallyMultiValuedMap<>();
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
    void testMap_oneEntry_null() {
        map.add("foo", null);
        assertThat(map.get("foo")).isEqualTo(null);
        assertThat(map.getFirst("foo")).isEqualTo(null);
        assertThat(map.getAll("foo")).isEqualTo(Collections.singletonList(null));
    }

    @Test
    void testMap_twoEntries_null() {
        map.add("foo", null);
        map.add("foo", "baz");
        assertThat(map.get("foo")).isEqualTo(Arrays.asList(null, "baz"));
        assertThat(map.getFirst("foo")).isEqualTo(null);
        assertThat(map.getAll("foo")).isEqualTo(Arrays.asList(null, "baz"));
    }

}
