package co.elastic.apm.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.source.SystemPropertyConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class PrefixingConfigurationSourceWrapperTest {

    private PrefixingConfigurationSourceWrapper sourceWrapper;

    @BeforeEach
    void setUp() {
        sourceWrapper = new PrefixingConfigurationSourceWrapper(new SystemPropertyConfigurationSource(), "elastic.apm.");
    }

    @Test
    void getValue() {
        System.setProperty("elastic.apm.foo", "bar");
        assertThat(sourceWrapper.getValue("foo")).isEqualTo("bar");
    }

}
