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

import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.common.util.SystemStandardOutputLogger;

import java.lang.management.ManagementFactory;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.List;

class BootstrapChecks {

    private final List<BootstrapCheck> bootstrapChecks;
    private final boolean bootstrapChecksEnabled;

    BootstrapChecks(boolean bootstrapChecksEnabled, BootstrapCheck... bootstrapChecks) {
        this.bootstrapChecks = Arrays.asList(bootstrapChecks);
        this.bootstrapChecksEnabled = bootstrapChecksEnabled;
    }

    static BootstrapChecks defaults() {
        if (System.getSecurityManager() == null) {
            return createDefaults();
        }

        return AccessController.doPrivileged(new PrivilegedAction<BootstrapChecks>() {
            @Override
            public BootstrapChecks run() {
                return createDefaults();
            }
        });
    }

    private static BootstrapChecks createDefaults() {
        boolean bootstrapChecksDisabled = Boolean.parseBoolean(System.getProperty("elastic.apm.disable_bootstrap_checks")) ||
            Boolean.parseBoolean(System.getenv("ELASTIC_APM_DISABLE_BOOTSTRAP_CHECKS"));
        String cmd = System.getProperty("sun.java.command");
        return new BootstrapChecks(!bootstrapChecksDisabled,
            new JavaVersionBootstrapCheck(JvmRuntimeInfo.ofCurrentVM()),
            new VerifyNoneBootstrapCheck(ManagementFactory.getRuntimeMXBean()),
            new JvmToolBootstrapCheck(cmd),
            new ExcludeJvmBootstrapCheck(cmd)
        );
    }

    /**
     * Returns {@code true} if the current VM passes the {@linkplain BootstrapChecks#defaults default bootstrap checks}
     * or if bootstrap checks are disabled.
     */
    boolean isPassing() {
        BootstrapCheck.BootstrapCheckResult result = new BootstrapCheck.BootstrapCheckResult();
        for (BootstrapCheck check : bootstrapChecks) {
            check.doBootstrapCheck(result);
        }

        if (result.isEmpty()) {
            return true;
        }
        boolean isPassing = true;
        if (result.hasErrors()) {
            if (bootstrapChecksEnabled) {
                isPassing = false;
                SystemStandardOutputLogger.stdErrWarn("Failed to start agent because of failing bootstrap checks.");
                SystemStandardOutputLogger.stdErrInfo("To override Java bootstrap checks, set the 'elastic.apm.disable_bootstrap_checks' System property, " +
                    "or the `ELASTIC_APM_DISABLE_BOOTSTRAP_CHECKS` environment variable, `to 'true'.");
            } else {
                SystemStandardOutputLogger.stdErrWarn("Bootstrap checks have failed. The agent will still start because bootstrap check have been disabled.");
            }
            SystemStandardOutputLogger.stdErrInfo("Note that we can not offer support for issues related to disabled bootstrap checks.");
            for (String msg : result.getErrors()) {
                SystemStandardOutputLogger.stdErrError(msg);
            }
        }
        if (result.hasWarnings()) {
            for (String msg : result.getWarnings()) {
                SystemStandardOutputLogger.stdErrWarn(msg);
            }
        }
        return isPassing;
    }

}
