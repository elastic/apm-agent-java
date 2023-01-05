package co.elastic.apm.agent.premain;

import co.elastic.apm.agent.common.util.WildcardMatcher;

import javax.annotation.Nullable;
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
 * <table border="3">
 *     <tr><th>System property name</th><th>Env variable name</th><th>Description</th></tr>
 *     <tr><td>elastic.apm.bootstrap_allowlist</td><td>ELASTIC_APM_BOOTSTRAP_ALLOWLIST</td><td>If set, the agent will be enabled
 *     only on JVMs of which command matches one of the patterns in the provided list</td></tr>
 *     <tr><td>elastic.apm.bootstrap_exclude_list</td><td>ELASTIC_APM_BOOTSTRAP_EXCLUDE_LIST</td><td>If set, the agent will be disabled
 *     on JVMs that contain a System property with one of the provided names in the list</td></tr>
 * </table>
 *
 * The allowlist option expects a comma-separated list of wild-card patterns. Such patterns may contain wildcards *, which match zero
 * or more characters. Examples: {@code foo*bar*baz*, *foo*}. Matching is case-insensitive by default. Prepending an element with
 * {@code (?-i) makes the matching case sensitive.
 *
 * If neither configurable option is set, the agent contains a builtin list of default disable System property names. For example, we know
 * that attaching the agent to ActiveMQ processes by accident may prevent it from starting.
 * This list is meant to grow based on users' feedback.
 */
public class ExcludeJvmBootstrapCheck implements BootstrapCheck {

    private static final List<String> defaultExcludeList = Arrays.asList("activemq.home", "activemq.base");

    @Nullable
    private List<WildcardMatcher> configuredAllowList;

    @Nullable
    private List<String> configuredExcludeList;

    public ExcludeJvmBootstrapCheck() {
        // todo - read configuration options and populate lists if necessary
    }

    // todo: wrap with doPrivileged?
    @Override
    public void doBootstrapCheck(BootstrapCheckResult result) {
        if (configuredAllowList != null) {
            if (WildcardMatcher.isNoneMatch(configuredAllowList, System.getProperty("sun.java.command"))) {
                // when specifically configuring allowlist, any non-matched command is implicitly excluded
                // todo - improve
                result.addError("Excluded because");
            }
            // No need to keep looking if allow list configured
            return;
        }

        List<String> excludeSystemProperties = (configuredExcludeList != null) ? configuredExcludeList : defaultExcludeList;
        for (String excludeSystemProperty : excludeSystemProperties) {
            if (System.getProperty(excludeSystemProperty) != null) {
                // todo - improve
                result.addError("Excluded because ...");
                return;
            }
        }
    }

    private List<WildcardMatcher> parse(String commaSeparatedList) {
        if (commaSeparatedList != null && commaSeparatedList.length() > 0) {
            final ArrayList<WildcardMatcher> result = new ArrayList<>();
            for (String part : commaSeparatedList.split(",")) {
                result.add(WildcardMatcher.valueOf(part.trim()));
            }
            return Collections.unmodifiableList(result);
        }
        return Collections.emptyList();
    }
}
