/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
        // JDK 11+
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
        "jdk.rmic/sun.rmi.rmic.Main",
        // JDK 8 (before JPMS)
        "com.sun.corba.se.impl.activation.ORBD",
        "com.sun.corba.se.impl.activation.ServerTool",
        "com.sun.corba.se.impl.naming.cosnaming.TransientNameServer",
        "com.sun.javafx.tools.packager.Main",
        "com.sun.java.util.jar.pack.Driver",
        "com.sun.tools.corba.se.idl.toJavaPortable.Compile",
        "com.sun.tools.example.debug.tty.TTY",
        "com.sun.tools.extcheck.Main",
        "com.sun.tools.hat.Main",
        "com.sun.tools.internal.jxc.SchemaGenerator",
        "com.sun.tools.internal.ws.WsGen",
        "com.sun.tools.internal.ws.WsImport",
        "com.sun.tools.internal.xjc.Driver",
        "com.sun.tools.javac.Main",
        "com.sun.tools.javadoc.Main",
        "com.sun.tools.javah.Main",
        "com.sun.tools.javap.Main",
        "com.sun.tools.jdeps.Main",
        "com.sun.tools.script.shell.Main",
        "jdk.jfr.internal.tool.Main",
        "jdk.nashorn.tools.Shell",
        "sun.applet.Main",
        "sun.jvm.hotspot.CLHSDB",
        "sun.jvm.hotspot.HSDB",
        "sun.jvm.hotspot.jdi.SADebugServer",
        "sun.rmi.registry.RegistryImpl",
        "sun.rmi.rmic.Main",
        "sun.rmi.server.Activation",
        "sun.rmi.transport.proxy.CGIHandler",
        "sun.security.tools.jarsigner.Main",
        "sun.security.tools.keytool.Main",
        "sun.security.tools.policytool.PolicyTool",
        "sun.tools.jar.Main",
        "sun.tools.jcmd.JCmd",
        "sun.tools.jconsole.JConsole",
        "sun.tools.jinfo.JInfo",
        "sun.tools.jmap.JMap",
        "sun.tools.jps.Jps",
        "sun.tools.jstack.JStack",
        "sun.tools.jstatd.Jstatd",
        "sun.tools.jstat.Jstat",
        "sun.tools.native2ascii.Main",
        "sun.tools.serialver.SerialVer",
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
