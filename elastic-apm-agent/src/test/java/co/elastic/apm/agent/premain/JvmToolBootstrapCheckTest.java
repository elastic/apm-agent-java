package co.elastic.apm.agent.premain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

class JvmToolBootstrapCheckTest {

    // this list was computed by trying every binary in ${JAVA_HOME}/bin folder
    @ParameterizedTest
    @ValueSource(strings = {
        "java.base/com.sun.java.util.jar.pack.Driver",
        "java.base/sun.security.tools.keytool.Main",
        "java.rmi/sun.rmi.registry.RegistryImpl",
        "java.rmi/sun.rmi.server.Activation",
        "jdk.aot/jdk.tools.jaotc.Main",
        "jdk.hotspot.agent/sun.jvm.hotspot.SALauncher",
        "jdk.jartool/sun.security.tools.jarsigner.Main",
        "jdk.javadoc/jdk.javadoc.internal.tool.Main",
        "jdk.jcmd/sun.tools.jcmd.JCmd",
        "jdk.jcmd/sun.tools.jps.Jps",
        "jdk.jfr/jdk.jfr.internal.tool.Main",
        "jdk.jlink/jdk.tools.jimage.Main",
        "jdk.jlink/jdk.tools.jlink.internal.Main",
        "jdk.jlink/jdk.tools.jmod.Main",
        "jdk.jshell/jdk.internal.jshell.tool.JShellToolProvider",
        "jdk.rmic/sun.rmi.rmic.Main"
    })
    void testJdkTool(String cmd) {
        assertThat(new JvmToolBootstrapCheck(cmd).isJdkTool())
            .describedAs("command '%s' should be detected as a JDK tool")
            .isTrue();
    }

    @Test
    void checkNotJdkTool() {
        checkNotJdkTool(null);
        checkNotJdkTool("");
    }

    private static void checkNotJdkTool(@Nullable String cmd) {
        assertThat(new JvmToolBootstrapCheck(cmd).isJdkTool())
            .describedAs("command '%s' should not be detected as a JDK tool")
            .isFalse();
    }

}
