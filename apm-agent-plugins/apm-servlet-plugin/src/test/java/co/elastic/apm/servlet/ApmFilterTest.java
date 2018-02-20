package co.elastic.apm.servlet;

import co.elastic.apm.impl.ElasticApmTracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;

class ApmFilterTest {

    private ApmFilter apmFilter = new ApmFilter(mock(ElasticApmTracer.class));

    @Test
    void getResult() {
        assertSoftly(softly -> {
            softly.assertThat(apmFilter.getResult(100)).isEqualTo("HTTP 1xx");
            softly.assertThat(apmFilter.getResult(199)).isEqualTo("HTTP 1xx");
            softly.assertThat(apmFilter.getResult(200)).isEqualTo("HTTP 2xx");
            softly.assertThat(apmFilter.getResult(299)).isEqualTo("HTTP 2xx");
            softly.assertThat(apmFilter.getResult(300)).isEqualTo("HTTP 3xx");
            softly.assertThat(apmFilter.getResult(399)).isEqualTo("HTTP 3xx");
            softly.assertThat(apmFilter.getResult(400)).isEqualTo("HTTP 4xx");
            softly.assertThat(apmFilter.getResult(499)).isEqualTo("HTTP 4xx");
            softly.assertThat(apmFilter.getResult(500)).isEqualTo("HTTP 5xx");
            softly.assertThat(apmFilter.getResult(599)).isEqualTo("HTTP 5xx");
            softly.assertThat(apmFilter.getResult(600)).isNull();
            softly.assertThat(apmFilter.getResult(20)).isNull();
            softly.assertThat(apmFilter.getResult(0)).isNull();
            softly.assertThat(apmFilter.getResult(-1)).isNull();
        });

    }
}
