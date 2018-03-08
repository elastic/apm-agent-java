package co.elastic.apm.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class MathUtilsTest {

    @Test
    void getNextPowerOf2() {
        assertSoftly(softly -> {
            softly.assertThat(MathUtils.getNextPowerOf2(-1)).isEqualTo(2);
            softly.assertThat(MathUtils.getNextPowerOf2(0)).isEqualTo(2);
            softly.assertThat(MathUtils.getNextPowerOf2(1)).isEqualTo(2);
            softly.assertThat(MathUtils.getNextPowerOf2(2)).isEqualTo(2);
            softly.assertThat(MathUtils.getNextPowerOf2(3)).isEqualTo(4);
            softly.assertThat(MathUtils.getNextPowerOf2(234234)).isEqualTo(262144);
        });
    }
}
