package co.elastic.apm.agent.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionTest {

    @Test
    void testVersion() {
        assertThat(Version.of("1.2.3")).isGreaterThan(Version.of("1.2.2"));
        assertThat(Version.of("1.2.3-SNAPSHOT")).isGreaterThan(Version.of("1.2.2"));
    }
}
