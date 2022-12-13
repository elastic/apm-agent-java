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

public class JvmToolBootstrapCheck implements BootstrapCheck{

    @Nullable
    private final String cmd;

    public JvmToolBootstrapCheck(String jvmCmd) {
        this.cmd = jvmCmd;
    }

    @Override
    public void doBootstrapCheck(BootstrapCheckResult result) {
        // JDK tools like 'jps' or 'keytool' should not be instrumented as it just makes them slower
        // they can be instrumented as a side effect of setting a global JAVA_TOOL_OPTIONS env variable
        if (isJdkTool()) {
            result.addError(String.format("JVM tool detected: '%s' agent will self-disable", cmd));
        }
    }

    public boolean isJdkTool() {
        if (cmd == null) {
            return false;
        }

        String[] parts = cmd.split("/");
        if (parts.length == 2) {
            // JDK with module system
            return parts[0].startsWith("jdk.") ||
                parts[0].startsWith("java.");
        } else if (parts.length == 1) {
            return cmd.startsWith("sun.") || cmd.startsWith("com.sun.") || cmd.startsWith("jdk.");
        }

        return false;
    }
}
