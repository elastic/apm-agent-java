package co.elastic.apm.agent.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionUtilsTest {

    @Test
    void testGetVersionFromPackage() {
        assertThat(VersionUtils.getVersionFromPackage(Test.class)).isNotEmpty();
        assertThat(VersionUtils.getVersionFromPackage(Test.class))
            .isEqualTo(VersionUtils.getVersion(Test.class, "org.junit.jupiter", "junit-jupiter-api"));
        // tests caching
        assertThat(VersionUtils.getVersion(Test.class, "org.junit.jupiter", "junit-jupiter-api"))
            .isSameAs(VersionUtils.getVersion(Test.class, "org.junit.jupiter", "junit-jupiter-api"));
    }

    @Test
    void getVersionFromPomProperties() {
        assertThat(VersionUtils.getVersionFromPomProperties(Assertions.class, "org.assertj", "assertj-core")).isNotEmpty();
        assertThat(VersionUtils.getVersionFromPomProperties(Assertions.class, "org.assertj", "assertj-core"))
            .isEqualTo(VersionUtils.getVersion(Assertions.class, "org.assertj", "assertj-core"));
        // tests caching
        assertThat(VersionUtils.getVersion(Assertions.class, "org.assertj", "assertj-core"))
            .isSameAs(VersionUtils.getVersion(Assertions.class, "org.assertj", "assertj-core"));
    }
}
