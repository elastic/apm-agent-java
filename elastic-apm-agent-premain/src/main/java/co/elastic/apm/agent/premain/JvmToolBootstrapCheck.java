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

import javax.annotation.Nullable;

public class JvmToolBootstrapCheck implements BootstrapCheck {

    @Nullable
    private final String cmd;

    public JvmToolBootstrapCheck(String jvmCmd) {
        this.cmd = jvmCmd;
    }

    @Override
    public void doBootstrapCheck(BootstrapCheckResult result) {
        // JDK tools like 'jps' or 'keytool' should not be instrumented as it just makes them slower
        // they can be instrumented as a side effect of setting a global JAVA_TOOL_OPTIONS env variable
        //
        // When this happens there is a message in standard error output: "Picked up JAVA_TOOL_OPTIONS: <...>"
        // The bootstrap checks errors & warnings are written to standard error too, thus the warning message
        // is likely to be written just next to this JVM message.
        checkJdkTool(cmd, result);
    }

    public static void checkJdkTool(String jvmCmd, BootstrapCheckResult result) {
        if (jvmCmd == null) {
            return;
        }

        String[] parts = jvmCmd.split(" ");
        if (parts.length > 0 && parts[0].endsWith(".jar")) {
            // java -jar xxx.jar
            return;
        }

        boolean isJdkTool = false;

        parts = jvmCmd.split("/");
        if (parts.length == 2) {
            // JDK with module system
            isJdkTool = parts[0].startsWith("jdk.") || parts[0].startsWith("java.");
        } else if (parts.length == 1) {
            isJdkTool = jvmCmd.startsWith("sun.") || jvmCmd.startsWith("com.sun.") || jvmCmd.startsWith("jdk.");
        } else {
            result.addWarn("Unexpected JVM command line syntax: " + jvmCmd);
        }
        if (isJdkTool) {
            result.addError(String.format("JVM tool detected: '%s', agent instrumentation on JVM tools adds unnecessary performance overhead",
                jvmCmd));
        }
    }
}
