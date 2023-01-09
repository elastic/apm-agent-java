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
    void checkDefaultExcludeList() {
        BootstrapCheck.BootstrapCheckResult result = runCheckWithConfigurationProperty(null, null, null, "cmd");
        assertThat(result.hasErrors()).isFalse();
        result = runCheckWithConfigurationProperty(null, null, "activemq.home", "cmd");
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    void checkConfiguredExcludeList() {
        BootstrapCheck.BootstrapCheckResult result = runCheckWithConfigurationProperty(EXCLUDE_LIST_SYSTEM_PROPERTY, "test1,test2", null, "cmd");
        assertThat(result.hasErrors()).isFalse();
        result = runCheckWithConfigurationProperty(EXCLUDE_LIST_SYSTEM_PROPERTY, "test1,test2", "test1", "cmd");
        assertThat(result.getErrors()).hasSize(1);
        result = runCheckWithConfigurationProperty(EXCLUDE_LIST_SYSTEM_PROPERTY, "test1,test2", "test2", "cmd");
        assertThat(result.getErrors()).hasSize(1);
        result = runCheckWithConfigurationProperty(EXCLUDE_LIST_SYSTEM_PROPERTY, "test1,test2", "activemq.home", "cmd");
        assertThat(result.hasErrors())
            .describedAs("Configured exclude list should override the default exclude list")
            .isFalse();
    }

    @Test
    void checkConfiguredAllowlist() {
        BootstrapCheck.BootstrapCheckResult result = runCheckWithConfigurationProperty(ALLOWLIST_SYSTEM_PROPERTY, "test1*,*test2", null, "test1cmd");
        assertThat(result.hasErrors())
            .describedAs("First wildcard pattern should be matched")
            .isFalse();
        result = runCheckWithConfigurationProperty(ALLOWLIST_SYSTEM_PROPERTY, "test1*,*test2", null, "cmdtest1");
        assertThat(result.hasErrors())
            .describedAs("No wildcard pattern should be matched")
            .isTrue();
        result = runCheckWithConfigurationProperty(ALLOWLIST_SYSTEM_PROPERTY, "test1*,*test2", null, "cmdtest2");
        assertThat(result.hasErrors())
            .describedAs("Second wildcard pattern should be matched")
            .isFalse();
        result = runCheckWithConfigurationProperty(ALLOWLIST_SYSTEM_PROPERTY, "test1*,*test2", "activemq.home", "cmdtest2");
        assertThat(result.hasErrors())
            .describedAs("Configured allow list should override exclude lists")
            .isFalse();
    }

    @Test
    void testEnvVariableNames() {
        assertThat(ALLOWLIST_ENV_VARIABLE).isEqualTo(ALLOWLIST_SYSTEM_PROPERTY.replace('.', '_').toUpperCase());
        assertThat(EXCLUDE_LIST_ENV_VARIABLE).isEqualTo(EXCLUDE_LIST_SYSTEM_PROPERTY.replace('.', '_').toUpperCase());
    }

    private BootstrapCheck.BootstrapCheckResult runCheckWithConfigurationProperty(@Nullable String configPropKey, @Nullable String configPropValue,
                                                                                  @Nullable String systemProp, String cmd) {
        BootstrapCheck.BootstrapCheckResult result = new BootstrapCheck.BootstrapCheckResult();
        try {
            if (configPropKey != null && configPropValue != null) {
                System.setProperty(configPropKey, configPropValue);
            }
            ExcludeJvmBootstrapCheck check = new ExcludeJvmBootstrapCheck(cmd);
            if (systemProp == null) {
                check.doBootstrapCheck(result);
            } else {
                runCheckWithSystemProperty(check, systemProp, result);
            }
        } finally {
            if (configPropKey != null) {
                System.clearProperty(configPropKey);
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
