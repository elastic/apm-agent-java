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

import java.util.ArrayList;
import java.util.List;

/**
 * A check that gets executed when the agent starts up.
 * If any check fails, the agent won't start unless the {@code elastic.apm.disable_bootstrap_checks} system property is set to true.
 */
public interface BootstrapCheck {

    /**
     * Performs the bootstrap check.
     *
     * @param result Add an error message to avoid the agent from starting up.
     */
    void doBootstrapCheck(BootstrapCheckResult result);

    class BootstrapCheckResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        void addError(String message) {
            errors.add(message);
        }

        void addWarn(String message) {
            warnings.add(message);
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public boolean isEmpty() {
            return warnings.isEmpty() && errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
