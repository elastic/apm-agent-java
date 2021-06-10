/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.premain;

import java.lang.management.ManagementFactory;
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
        return new BootstrapChecks(!Boolean.parseBoolean(System.getProperty("elastic.apm.disable_bootstrap_checks")),
            new JavaVersionBootstrapCheck(JvmRuntimeInfo.ofCurrentVM()), new VerifyNoneBootstrapCheck(ManagementFactory.getRuntimeMXBean()));
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
                System.err.println("ERROR - Failed to start agent because of failing bootstrap checks.");
                System.err.println("To override Java version verification, set the 'elastic.apm.disable_bootstrap_checks' System property to 'true'.");
            } else {
                System.err.println("WARNING - Bootstrap checks have failed. The agent will still start because bootstrap check have been disabled.");
            }
            System.err.println("Note that we can not offer support for issues related to disabled bootstrap checks.");
            for (String msg : result.getErrors()) {
                System.err.println(msg);
            }
        }
        if (result.hasWarnings()) {
            for (String msg : result.getWarnings()) {
                System.err.println(msg);
            }
        }
        return isPassing;
    }

}
