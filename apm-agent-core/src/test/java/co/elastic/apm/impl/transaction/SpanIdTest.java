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

    @Test
    void testEmptyId() {
        assertThat(new SpanId().asLong()).isEqualTo(0);
    }

    @Test
    void testClear() {
        final SpanId spanId = new SpanId();
        spanId.setToRandomValue();
        assertThat(spanId.asLong()).isNotEqualTo(0);

        spanId.resetState();
        assertThat(new SpanId().asLong()).isEqualTo(0);
    }

    @Test
    void setAndGetLong() {
        final SpanId spanId = new SpanId();
        spanId.setLong(42);
        assertThat(spanId.asLong()).isEqualTo(42);
    }
}
