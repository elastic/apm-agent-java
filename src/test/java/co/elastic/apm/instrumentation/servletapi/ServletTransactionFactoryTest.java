package co.elastic.apm.instrumentation.servletapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ServletTransactionFactoryTest {

    @Test
    void getResult() {
        assertSoftly(softly -> {
            softly.assertThat(ServletTransactionFactory.getResult(100)).isEqualTo("HTTP 1xx");
            softly.assertThat(ServletTransactionFactory.getResult(199)).isEqualTo("HTTP 1xx");
            softly.assertThat(ServletTransactionFactory.getResult(200)).isEqualTo("HTTP 2xx");
            softly.assertThat(ServletTransactionFactory.getResult(299)).isEqualTo("HTTP 2xx");
            softly.assertThat(ServletTransactionFactory.getResult(300)).isEqualTo("HTTP 3xx");
            softly.assertThat(ServletTransactionFactory.getResult(399)).isEqualTo("HTTP 3xx");
            softly.assertThat(ServletTransactionFactory.getResult(400)).isEqualTo("HTTP 4xx");
            softly.assertThat(ServletTransactionFactory.getResult(499)).isEqualTo("HTTP 4xx");
            softly.assertThat(ServletTransactionFactory.getResult(500)).isEqualTo("HTTP 5xx");
            softly.assertThat(ServletTransactionFactory.getResult(599)).isEqualTo("HTTP 5xx");
            softly.assertThat(ServletTransactionFactory.getResult(600)).isNull();
            softly.assertThat(ServletTransactionFactory.getResult(20)).isNull();
            softly.assertThat(ServletTransactionFactory.getResult(0)).isNull();
            softly.assertThat(ServletTransactionFactory.getResult(-1)).isNull();
        });

    }
}
