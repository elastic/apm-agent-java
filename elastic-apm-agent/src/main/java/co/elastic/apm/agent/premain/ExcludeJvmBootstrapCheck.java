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

import co.elastic.apm.agent.common.util.SystemStandardOutputLogger;
import co.elastic.apm.agent.common.util.WildcardMatcher;

import javax.annotation.Nullable;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * In some cases, users may cast a too-wide net to instrument their Java processes, for example when setting the {@code JAVA_TOOL_OPTIONS}
 * environment variable globally on a host/container.
 * In such cases, we should provide a configurable way to exclude JVMs from being instrumented, or to specifically allow when necessary.
 *
 * For this purpose, we have the following bootstrap configuration options available:
 * <table border="1">
 *     <caption>Configuration options</caption>
 *     <tr><th>System property name</th><th>Env variable name</th><th>Description</th></tr>
 *     <tr><td>{@value #ALLOWLIST_SYSTEM_PROPERTY}</td><td>{@value #ALLOWLIST_ENV_VARIABLE}</td><td>If set, the agent will be enabled
 *     <b>only</b> on JVMs of which command matches one of the patterns in the provided list</td></tr>
 *     <tr><td>{@value EXCLUDE_LIST_SYSTEM_PROPERTY}</td><td>{@value #EXCLUDE_LIST_ENV_VARIABLE}</td><td>If set, the agent will be disabled
 *     on JVMs that contain a System property with one of the provided names in the list</td></tr>
 * </table>
 *
 * The allowlist option expects a comma-separated list of wild-card patterns. Such patterns may contain wildcards *, which match zero
 * or more characters. Examples: {@code foo*bar*baz*, *foo*}. Matching is case-insensitive by default. Prepending an element with
 * {@code (?-i)} makes the matching case-sensitive.
 *
 * Some examples:
 * <ul>
 *     <li>
 *         Example 1: allow JVM attachment only on Tomcat and some proprietary Java apps:
 *         {@code -Delastic.apm.bootstrap_allowlist=org.apache.catalina.startup.Bootstrap*,my.cool.app.*}
 *     </li>
 *     <li>
 *         Example 2: disable when some custom System properties are set:
 *         {@code -Delastic.apm.bootstrap_exclude_list=custom.property.1,custom.property.2}
 *     </li>
 * </ul>
 */
public class ExcludeJvmBootstrapCheck implements BootstrapCheck {

    public static final String ALLOWLIST_SYSTEM_PROPERTY = "elastic.apm.bootstrap_allowlist";
    public static final String ALLOWLIST_ENV_VARIABLE = "ELASTIC_APM_BOOTSTRAP_ALLOWLIST";
    public static final String EXCLUDE_LIST_SYSTEM_PROPERTY = "elastic.apm.bootstrap_exclude_list";
    public static final String EXCLUDE_LIST_ENV_VARIABLE = "ELASTIC_APM_BOOTSTRAP_EXCLUDE_LIST";

    @Nullable
    private final String cmd;

    @Nullable
    private String allowListRaw;

    @Nullable
    private List<WildcardMatcher> configuredAllowList;

    @Nullable
    private String excludeListRaw;

    @Nullable
    private List<String> configuredExcludeList;

    public ExcludeJvmBootstrapCheck(@Nullable String cmd) {
        this.cmd = cmd;

        allowListRaw = System.getProperty(ALLOWLIST_SYSTEM_PROPERTY);
        if (allowListRaw == null) {
            allowListRaw = System.getenv(ALLOWLIST_ENV_VARIABLE);
        }
        if (allowListRaw != null) {
            configuredAllowList = parse(allowListRaw, new WildCardMatcherConverter());
        }

        excludeListRaw = System.getProperty(EXCLUDE_LIST_SYSTEM_PROPERTY);
        if (excludeListRaw == null) {
            excludeListRaw = System.getenv(EXCLUDE_LIST_ENV_VARIABLE);
        }
        if (excludeListRaw != null) {
            configuredExcludeList = parse(excludeListRaw, new StringConverter());
        }
    }

    @Override
    public void doBootstrapCheck(final BootstrapCheckResult result) {
        if (configuredAllowList != null) {
            if (cmd == null || cmd.isEmpty()) {
                result.addWarn(String.format("'%s' or '%s' are configured, but they cannot be matched with the JVM command," +
                        "as it is not properly discovered: '%s'",
                    ALLOWLIST_SYSTEM_PROPERTY, ALLOWLIST_ENV_VARIABLE, cmd));
            } else {
                if (WildcardMatcher.isNoneMatch(configuredAllowList, cmd)) {
                    // when configuring allowlist, any JVM with non-matched command is implicitly excluded
                    result.addError(String.format("'%s' or '%s' are configured with the pattern list '%s', which does not match this JVM's command: '%s'",
                        ALLOWLIST_SYSTEM_PROPERTY, ALLOWLIST_ENV_VARIABLE, allowListRaw, cmd));
                } else {
                    SystemStandardOutputLogger.stdOutInfo(String.format("Attaching an agent to this process as its command '%s' matches the " +
                            "configured allowlist: '%s'%n", cmd, allowListRaw));
                }
                // No need to keep looking if allow list configured and the command doesn't match
                return;
            }
        }

        if (configuredExcludeList != null) {
            if (System.getSecurityManager() == null) {
                doExcludeListCheck(result, configuredExcludeList);
                return;
            }

            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    doExcludeListCheck(result, configuredExcludeList);
                    return null;
                }
            });
        }
    }

    private void doExcludeListCheck(BootstrapCheckResult result, List<String> excludeSystemProperties) {
        for (String excludeSystemProperty : excludeSystemProperties) {
            if (System.getProperty(excludeSystemProperty) != null) {
                result.addError(String.format("Found the '%s' System property, which is configured to cause the exclusion of this JVM. " +
                        "Change either the '%s' System property or '%s' " +
                        "environment variable setting in order to override this exclusion. Current configured value is: '%s'.",
                    excludeSystemProperty, EXCLUDE_LIST_SYSTEM_PROPERTY, EXCLUDE_LIST_ENV_VARIABLE, excludeListRaw));
                return;
            }
        }
    }

    private <T> List<T> parse(String commaSeparatedList, ListItemConverter<T> converter) {
        if (commaSeparatedList != null && commaSeparatedList.length() > 0) {
            final ArrayList<T> result = new ArrayList<>();
            for (String part : commaSeparatedList.split(",")) {
                result.add(converter.convert(part.trim()));
            }
            return Collections.unmodifiableList(result);
        }
        return Collections.emptyList();
    }

    private interface ListItemConverter<T> {
        T convert(String rawValue);
    }

    private static class WildCardMatcherConverter implements ListItemConverter<WildcardMatcher> {
        @Override
        public WildcardMatcher convert(String rawValue) {
            return WildcardMatcher.valueOf(rawValue);
        }
    }

    private static class StringConverter implements ListItemConverter<String> {
        @Override
        public String convert(String rawValue) {
            return rawValue;
        }
    }
}
