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

import javax.annotation.Nullable;

import static co.elastic.apm.agent.premain.ExcludeJvmBootstrapCheck.ALLOWLIST_ENV_VARIABLE;
import static co.elastic.apm.agent.premain.ExcludeJvmBootstrapCheck.ALLOWLIST_SYSTEM_PROPERTY;
import static co.elastic.apm.agent.premain.ExcludeJvmBootstrapCheck.EXCLUDE_LIST_ENV_VARIABLE;
import static co.elastic.apm.agent.premain.ExcludeJvmBootstrapCheck.EXCLUDE_LIST_SYSTEM_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;

class ExcludeJvmBootstrapCheckTest {

    @Test
    void checkConfiguredExcludeList() {
        BootstrapCheck.BootstrapCheckResult result = runCheckWithConfigurationProperty(null, "test1,test2", null, "cmd");
        assertThat(result.hasErrors()).isFalse();
        result = runCheckWithConfigurationProperty(null, "test1,test2", "test1", "cmd");
        assertThat(result.getErrors()).hasSize(1);
        result = runCheckWithConfigurationProperty(null, "test1,test2", "test2", "cmd");
        assertThat(result.getErrors()).hasSize(1);
        result = runCheckWithConfigurationProperty("test2*", "test1,test2", "test2", "test2cmd");
        assertThat(result.hasErrors())
            .describedAs("Configured allow list should override exclude lists")
            .isFalse();
    }

    @Test
    void checkConfiguredAllowlist() {
        BootstrapCheck.BootstrapCheckResult result = runCheckWithConfigurationProperty("test1*,*test2", null, null, "test1cmd");
        assertThat(result.hasErrors())
            .describedAs("First wildcard pattern should be matched")
            .isFalse();
        result = runCheckWithConfigurationProperty("test1*,*test2", null, null, "cmdtest1");
        assertThat(result.hasErrors())
            .describedAs("No wildcard pattern should be matched")
            .isTrue();
        result = runCheckWithConfigurationProperty("test1*,*test2", null, null, "cmdtest2");
        assertThat(result.hasErrors())
            .describedAs("Second wildcard pattern should be matched")
            .isFalse();
    }

    @Test
    void testEnvVariableNames() {
        assertThat(ALLOWLIST_ENV_VARIABLE).isEqualTo(ALLOWLIST_SYSTEM_PROPERTY.replace('.', '_').toUpperCase());
        assertThat(EXCLUDE_LIST_ENV_VARIABLE).isEqualTo(EXCLUDE_LIST_SYSTEM_PROPERTY.replace('.', '_').toUpperCase());
    }

    private BootstrapCheck.BootstrapCheckResult runCheckWithConfigurationProperty(@Nullable String allowlistValue,
                                                                                  @Nullable String excludeListValue,
                                                                                  @Nullable String runtimeSystemProp, String cmd) {
        BootstrapCheck.BootstrapCheckResult result = new BootstrapCheck.BootstrapCheckResult();
        try {
            if (allowlistValue != null) {
                System.setProperty(ALLOWLIST_SYSTEM_PROPERTY, allowlistValue);
            }
            if (excludeListValue != null) {
                System.setProperty(EXCLUDE_LIST_SYSTEM_PROPERTY, excludeListValue);
            }
            ExcludeJvmBootstrapCheck check = new ExcludeJvmBootstrapCheck(cmd);
            if (runtimeSystemProp == null) {
                check.doBootstrapCheck(result);
            } else {
                runCheckWithSystemProperty(check, runtimeSystemProp, result);
            }
        } finally {
            if (allowlistValue != null) {
                System.clearProperty(ALLOWLIST_SYSTEM_PROPERTY);
            }
            if (excludeListValue != null) {
                System.clearProperty(EXCLUDE_LIST_SYSTEM_PROPERTY);
            }
        }
        return result;
    }

    private void runCheckWithSystemProperty(ExcludeJvmBootstrapCheck check, String prop, BootstrapCheck.BootstrapCheckResult result) {
        try {
            System.setProperty(prop, "test");
            check.doBootstrapCheck(result);
        } finally {
            System.clearProperty(prop);
        }
    }
}
