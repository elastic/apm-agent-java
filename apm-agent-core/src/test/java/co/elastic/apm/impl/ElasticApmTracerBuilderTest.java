package co.elastic.apm.impl;

import co.elastic.apm.configuration.CoreConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ElasticApmTracerBuilderTest {

    @Test
    void testMissingDefaultValues() {
        final ElasticApmTracer noopTracer = ElasticApmTracer.builder().build();

        assertThat(noopTracer.getConfig(CoreConfiguration.class).isActive()).isFalse();
        assertThat(noopTracer.startTransaction().isNoop()).isTrue();
    }
}
