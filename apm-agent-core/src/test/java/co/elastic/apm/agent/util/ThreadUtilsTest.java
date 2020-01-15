package co.elastic.apm.agent.util;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadUtilsTest {

    @Test
    public void testAddElasticApmThreadPrefix() {
        String purpose = RandomStringUtils.randomAlphanumeric(10);
        String prefixedThreadName = ThreadUtils.addElasticApmThreadPrefix(purpose);
        assertThat(prefixedThreadName).isEqualTo("elastic-apm-"+purpose);
    }
}
