package co.elastic.apm.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ResultUtilTest {

    @Test
    void getResult() {
        assertSoftly(softly -> {
            softly.assertThat(ResultUtil.getResultByHttpStatus(100)).isEqualTo("HTTP 1xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(199)).isEqualTo("HTTP 1xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(200)).isEqualTo("HTTP 2xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(299)).isEqualTo("HTTP 2xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(300)).isEqualTo("HTTP 3xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(399)).isEqualTo("HTTP 3xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(400)).isEqualTo("HTTP 4xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(499)).isEqualTo("HTTP 4xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(500)).isEqualTo("HTTP 5xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(599)).isEqualTo("HTTP 5xx");
            softly.assertThat(ResultUtil.getResultByHttpStatus(600)).isNull();
            softly.assertThat(ResultUtil.getResultByHttpStatus(20)).isNull();
            softly.assertThat(ResultUtil.getResultByHttpStatus(0)).isNull();
            softly.assertThat(ResultUtil.getResultByHttpStatus(-1)).isNull();
        });
    }

}
