package co.elastic.apm.impl.transaction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpanIdTest {

    @Test
    void testCopyFrom() {
        SpanId spanId1 = new SpanId();
        SpanId spanId2 = new SpanId();
        spanId1.setToRandomValue();
        spanId2.copyFrom(spanId1);
        assertThat(spanId1).isEqualTo(spanId2);
    }
}
