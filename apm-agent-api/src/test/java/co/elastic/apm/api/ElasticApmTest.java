package co.elastic.apm.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticApmTest {

    @BeforeEach
    void setUp() {
        ElasticApm.INSTANCE.unregister();
    }

    @Test
    void getNoop() {
        assertThat(ElasticApm.get()).isNotNull();
    }

    @Test
    void currentTransactionNoop() {
        assertThat(ElasticApm.get().currentTransaction()).isSameAs(ElasticApm.NoopTracer.NoopTransaction.INSTANCE);
    }

    @Test
    void currentSpanNoop() {
        assertThat(ElasticApm.get().currentSpan()).isSameAs(ElasticApm.NoopTracer.NoopSpan.INSTANCE);
    }
}
