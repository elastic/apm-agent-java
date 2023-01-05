package co.elastic.apm.agent.premain;

import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

class ExcludeJvmBootstrapCheckTest {

    public static final String ALLOWLIST_PROPERTY = "elastic.apm.bootstrap_allowlist";
    public static final String EXCLUDE_PROPERTY = "elastic.apm.bootstrap_exclude_list";

    @Test
    void checkDefaultExcludeList() {
        BootstrapCheck.BootstrapCheckResult result = runCheckWithConfigurationProperty(null, null, null, "cmd");
        assertThat(result.hasErrors()).isFalse();
        result = runCheckWithConfigurationProperty(null, null, "activemq.home", "cmd");
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    void checkConfiguredExcludeList() {
        BootstrapCheck.BootstrapCheckResult result = runCheckWithConfigurationProperty(EXCLUDE_PROPERTY, "test1,test2", null, "cmd");
        assertThat(result.hasErrors()).isFalse();
        result = runCheckWithConfigurationProperty(EXCLUDE_PROPERTY, "test1,test2", "test1", "cmd");
        assertThat(result.getErrors()).hasSize(1);
        result = runCheckWithConfigurationProperty(EXCLUDE_PROPERTY, "test1,test2", "test2", "cmd");
        assertThat(result.getErrors()).hasSize(1);
        result = runCheckWithConfigurationProperty(EXCLUDE_PROPERTY, "test1,test2", "activemq.home", "cmd");
        assertThat(result.hasErrors())
            .describedAs("Configured exclude list should override the default exclude list")
            .isFalse();
    }

    @Test
    void checkConfiguredAllowlist() {
        BootstrapCheck.BootstrapCheckResult result = runCheckWithConfigurationProperty(ALLOWLIST_PROPERTY, "test1*,*test2", null, "test1cmd");
        assertThat(result.hasErrors())
            .describedAs("First wildcard pattern should be matched")
            .isFalse();
        result = runCheckWithConfigurationProperty(ALLOWLIST_PROPERTY, "test1*,*test2", null, "cmdtest1");
        assertThat(result.hasErrors())
            .describedAs("No wildcard pattern should be matched")
            .isTrue();
        result = runCheckWithConfigurationProperty(ALLOWLIST_PROPERTY, "test1*,*test2", null, "cmdtest2");
        assertThat(result.hasErrors())
            .describedAs("Second wildcard pattern should be matched")
            .isFalse();
        result = runCheckWithConfigurationProperty(ALLOWLIST_PROPERTY, "test1*,*test2", "activemq.home", "cmdtest2");
        assertThat(result.hasErrors())
            .describedAs("Configured allow list should override exclude lists")
            .isFalse();
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
