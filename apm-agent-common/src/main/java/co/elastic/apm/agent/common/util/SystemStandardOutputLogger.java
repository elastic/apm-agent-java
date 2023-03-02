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
package co.elastic.apm.agent.common.util;

import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A utility for writing to System standard output and standard error output streams.
 * Prints can be disabled through the {@code elastic.apm.system_output_disabled} system property or the corresponding
 * {@code ELASTIC_APM_SYSTEM_OUTPUT_DISABLED} environment variable.
 * <p>
 * Important: The logic here is replicated in IndyBootstrapDispatcher, as it cannot access this class directly.
 */
public class SystemStandardOutputLogger {
    private static final String DISABLED_SYSTEM_PROPERTY = "elastic.apm.system_output_disabled";
    private static final String DISABLED_ENV_VARIABLE = "ELASTIC_APM_SYSTEM_OUTPUT_DISABLED";

    private static final String LINE_PREFIX = "[elastic-apm-agent]";

    private static final boolean disabled;

    static {
        if (System.getSecurityManager() == null) {
            disabled = isDisabledThroughConfiguration();
        } else {
            disabled = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                    return isDisabledThroughConfiguration();
                }
            });
        }
    }

    public static boolean isDisabled() {
        return disabled;
    }

    private static boolean isDisabledThroughConfiguration() {
        if (System.getSecurityManager() == null) {
            return System.getProperty(DISABLED_SYSTEM_PROPERTY) != null || System.getenv(DISABLED_ENV_VARIABLE) != null;
        } else {
            try {
                return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                    @Override
                    public Boolean run() {
                        return System.getProperty(DISABLED_SYSTEM_PROPERTY) != null || System.getenv(DISABLED_ENV_VARIABLE) != null;
                    }
                });
            } catch (AccessControlException ace) {
                // we can't use JVM system properties because security manager prevents it, we'd better be able to log something for the end user
                return false;
            }
        }
    }

    public static void printStackTrace(Throwable throwable) {
        if (!disabled) {
            throwable.printStackTrace();
        }
    }

    private static void printToStdOut(String level, String message) {
        if (!disabled) {
            System.out.printf("%s %s %s%n", LINE_PREFIX, level, message);
        }
    }

    private static void printToStdErr(String level, String message) {
        if (!disabled) {
            System.err.printf("%s %s %s%n", LINE_PREFIX, level, message);
        }
    }

    public static void stdOutInfo(String message) {
        printToStdOut("INFO", message);
    }

    public static void stdOutWarn(String message) {
        printToStdOut("WARN", message);
    }

    public static void stdOutError(String message) {
        printToStdOut("ERROR", message);
    }

    public static void stdErrInfo(String message) {
        printToStdErr("INFO", message);
    }

    public static void stdErrWarn(String message) {
        printToStdErr("WARN", message);
    }

    public static void stdErrError(String message) {
        printToStdErr("ERROR", message);
    }
}
