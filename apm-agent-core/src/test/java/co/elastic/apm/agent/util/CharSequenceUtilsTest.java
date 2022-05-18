package co.elastic.apm.agent.util;

import org.junit.jupiter.api.Test;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

class CharSequenceUtilsTest {

    @Test
    void testEqualsHashCode() {

        assertThat(CharSequenceUtils.equals(null, null)).isTrue();

        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        checkEqual(sb1, sb2);
        checkEqual(sb1, sb1); // should be equal to itself when empty

        sb1.append("a");
        checkNotEqual(sb1, sb2);
        checkEqual(sb1, sb1); // should be equal to itself when not empty

        sb2.append("a");
        checkEqual(sb1, sb2);
    }

    private void checkEqual(CharSequence cs1, CharSequence cs2){
        assertThat(CharSequenceUtils.equals(cs1, cs2)).isTrue();
        assertThat(CharSequenceUtils.hashCode(cs1)).isEqualTo(CharSequenceUtils.hashCode(cs2));
    }

    private void checkNotEqual(CharSequence cs1, CharSequence cs2){
        assertThat(CharSequenceUtils.equals(cs1, cs2)).isFalse();
        assertThat(CharSequenceUtils.hashCode(cs1)).isNotEqualTo(CharSequenceUtils.hashCode(cs2));
    }

}
