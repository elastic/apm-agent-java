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
 *     <tr><td>elastic.apm.bootstrap_allowlist</td><td>ELASTIC_APM_BOOTSTRAP_ALLOWLIST</td><td>If set, the agent will be enabled
 *     <b>only</b> on JVMs of which command matches one of the patterns in the provided list</td></tr>
 *     <tr><td>elastic.apm.bootstrap_exclude_list</td><td>ELASTIC_APM_BOOTSTRAP_EXCLUDE_LIST</td><td>If set, the agent will be disabled
 *     on JVMs that contain a System property with one of the provided names in the list</td></tr>
 * </table>
 *
 * The allowlist option expects a comma-separated list of wild-card patterns. Such patterns may contain wildcards *, which match zero
 * or more characters. Examples: {@code foo*bar*baz*, *foo*}. Matching is case-insensitive by default. Prepending an element with
 * {@code (?-i)} makes the matching case-sensitive.
 *
 * If neither configurable option is set, the agent contains a builtin list of default disable System property names. For example, we know
 * that attaching the agent to ActiveMQ processes by accident may prevent it from starting.
 * This list is meant to grow based on users' feedback.
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

    private static final List<String> defaultExcludeList = Arrays.asList("activemq.home", "activemq.base");

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

        allowListRaw = System.getProperty("elastic.apm.bootstrap_allowlist");
        if (allowListRaw == null) {
            allowListRaw = System.getenv("ELASTIC_APM_BOOTSTRAP_ALLOWLIST");
        }
        if (allowListRaw != null) {
            configuredAllowList = parse(allowListRaw, new WildCardMatcherConverter());
        }

        excludeListRaw = System.getProperty("elastic.apm.bootstrap_exclude_list");
        if (excludeListRaw == null) {
            excludeListRaw = System.getenv("ELASTIC_APM_BOOTSTRAP_EXCLUDE_LIST");
        }
        if (excludeListRaw != null) {
            configuredExcludeList = parse(excludeListRaw, new StringConverter());
        }
    }

    @Override
    public void doBootstrapCheck(final BootstrapCheckResult result) {
        if (cmd != null && configuredAllowList != null) {
            if (WildcardMatcher.isNoneMatch(configuredAllowList, cmd)) {
                // when configuring allowlist, any JVM with non-matched command is implicitly excluded
                result.addError(String.format("`elastic.apm.bootstrap_allowlist` or `ELASTIC_APM_BOOTSTRAP_ALLOWLIST` are configured with " +
                "the pattern list '%s', which does not match this JVM's command: '%s'", allowListRaw, cmd));
            }
            // No need to keep looking if allow list configured
            return;
        }

        final List<String> excludeSystemProperties = (configuredExcludeList != null) ? configuredExcludeList : defaultExcludeList;
        if (System.getSecurityManager() == null) {
            doExcludeListCheck(result, excludeSystemProperties);
            return;
        }

        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                doExcludeListCheck(result, excludeSystemProperties);
                return null;
            }
        });

        doExcludeListCheck(result, excludeSystemProperties);
    }

    private void doExcludeListCheck(BootstrapCheckResult result, List<String> excludeSystemProperties) {
        for (String excludeSystemProperty : excludeSystemProperties) {
            if (System.getProperty(excludeSystemProperty) != null) {
                result.addError(String.format("Found the '%s' System property, which is configured to cause the exclusion of this JVM. " +
                        "Change either the `elastic.apm.bootstrap_exclude_list` System property or `ELASTIC_APM_BOOTSTRAP_EXCLUDE_LIST` " +
                        "environment variable setting in order to override this exclusion. Current configured value is: '%s'.",
                    excludeSystemProperty, excludeListRaw));
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
