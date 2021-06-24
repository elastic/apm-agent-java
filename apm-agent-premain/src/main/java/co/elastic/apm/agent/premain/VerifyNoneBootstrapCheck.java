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

import java.lang.management.RuntimeMXBean;
import java.util.List;

public class VerifyNoneBootstrapCheck implements BootstrapCheck {

    private final RuntimeMXBean runtimeMXBean;

    public VerifyNoneBootstrapCheck(RuntimeMXBean runtimeMXBean) {
        this.runtimeMXBean = runtimeMXBean;
    }

    @Override
    public void doBootstrapCheck(BootstrapCheckResult result) {
        List<String> inputArguments = runtimeMXBean.getInputArguments();
        if (inputArguments.contains("-Xverify:none") || inputArguments.contains("-noverify")) {
            result.addWarn("WARNING: -Xverify:none and -noverify are not supported by the Elastic APM Java Agent. " +
                "In an upcoming version, the agent will not start when these flags are set, " +
                "unless the system property elastic.apm.disable_bootstrap_checks is set to true.");
        }
    }
}
