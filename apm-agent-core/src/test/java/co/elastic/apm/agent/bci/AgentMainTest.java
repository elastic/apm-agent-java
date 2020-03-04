package co.elastic.apm.agent.bci;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMainTest {

    @Test
    void java6AndEarlierNotSupported() {
        checkNotSupported("1.5.0");
        checkNotSupported("1.6.0"); // 211
    }

    @Test
    void java7AllVersionsSupported() {
        checkSupported("1.7.0");
        checkSupported("1.7.0_1");
        checkSupported("1.7.0_241");
    }

    @Test
    void java8OnlySupportedAfterUpdate40() {
        checkNotSupported("1.8.0");
        checkNotSupported("1.8.0_1");
        checkNotSupported("1.8.0_39");
        checkSupported("1.8.0_40");
        checkSupported("1.8.0_241");
    }

    @Test
    void java9AndLaterAllVersionsSupported() {
        checkSupported("9");
        checkSupported("9.0.1");
        checkSupported("9.0.4");
        checkSupported("10");
        checkSupported("10.0.1");
        checkSupported("10.0.2");
        checkSupported("11");
        checkSupported("11.0.1");
        checkSupported("11.0.2");
        checkSupported("11.0.3");
        checkSupported("11.0.4");
        checkSupported("11.0.5");
        checkSupported("11.0.6");
        checkSupported("12");
        checkSupported("12.0.1");
        checkSupported("12.0.2");
        checkSupported("13");
        checkSupported("13.0.1");
        checkSupported("13.0.2");
        checkSupported("14");
    }

    @Test
    void notSupportedwithGarbage() {
        checkNotSupported("1.8.0_aaa");
    }

    private static void checkSupported(String version) {
        assertThat(AgentMain.isJavaVersionSupported(version))
            .describedAs("java.version = %s should be supported", version)
            .isTrue();
    }

    private static void checkNotSupported(String version) {
        assertThat(AgentMain.isJavaVersionSupported(version))
            .describedAs("java.version = %s should not be supported", version)
            .isFalse();
    }

}
