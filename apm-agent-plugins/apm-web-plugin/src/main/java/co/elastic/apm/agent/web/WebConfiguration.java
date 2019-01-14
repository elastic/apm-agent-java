/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.web;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.ListValueConverter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WebConfiguration extends ConfigurationOptionProvider {

    private static final String HTTP_CATEGORY = "HTTP";
    private final ConfigurationOption<EventType> captureBody = ConfigurationOption.enumOption(EventType.class)
        .key("capture_body")
        .configurationCategory(HTTP_CATEGORY)
        .tags("performance")
        .description("For transactions that are HTTP requests, the Java agent can optionally capture the request body (e.g. POST " +
            "variables).\n" +
            "\n" +
            "If the request has a body and this setting is disabled, the body will be shown as [REDACTED].\n" +
            "\n" +
            "This option is case-insensitive.\n" +
            "\n" +
            "NOTE: Currently, only `application/x-www-form-urlencoded` (form parameters) are supported.\n" +
            "Forms which include a file upload (`multipart/form-data`) are not supported.\n" +
            "\n" +
            "WARNING: request bodies often contain sensitive values like passwords, credit card numbers etc." +
            "If your service handles data like this, we advise to only enable this feature with care.")
        .dynamic(true)
        .buildWithDefault(EventType.OFF);

    private final ConfigurationOption<Boolean> captureHeaders = ConfigurationOption.booleanOption()
        .key("capture_headers")
        .configurationCategory(HTTP_CATEGORY)
        .tags("performance")
        .description("If set to `true`,\n" +
            "the agent will capture request and response headers, including cookies.\n" +
            "\n" +
            "NOTE: Setting this to `false` reduces network bandwidth, disk space and object allocations.")
        .dynamic(true)
        .buildWithDefault(true);

    private final ConfigurationOption<List<WildcardMatcher>> ignoreUrls = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("ignore_urls")
        .configurationCategory(HTTP_CATEGORY)
        .description("Used to restrict requests to certain URLs from being instrumented.\n" +
            "\n" +
            "This property should be set to an array containing one or more strings.\n" +
            "When an incoming HTTP request is detected, its URL will be tested against each element in this list.\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION + "\n" +
            "\n" +
            "NOTE: All errors that are captured during a request to an ignored URL are still sent to the APM Server regardless of " +
            "this setting.")
        .dynamic(true)
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.valueOf("/VAADIN/*"),
            WildcardMatcher.valueOf("/heartbeat*"),
            WildcardMatcher.valueOf("/favicon.ico"),
            WildcardMatcher.valueOf("*.js"),
            WildcardMatcher.valueOf("*.css"),
            WildcardMatcher.valueOf("*.jpg"),
            WildcardMatcher.valueOf("*.jpeg"),
            WildcardMatcher.valueOf("*.png"),
            WildcardMatcher.valueOf("*.gif"),
            WildcardMatcher.valueOf("*.webp"),
            WildcardMatcher.valueOf("*.svg"),
            WildcardMatcher.valueOf("*.woff"),
            WildcardMatcher.valueOf("*.woff2")
        ));
    private final ConfigurationOption<List<WildcardMatcher>> ignoreUserAgents = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("ignore_user_agents")
        .configurationCategory(HTTP_CATEGORY)
        .description("Used to restrict requests from certain User-Agents from being instrumented.\n" +
            "\n" +
            "When an incoming HTTP request is detected,\n" +
            "the User-Agent from the request headers will be tested against each element in this list.\n" +
            "Example: `curl/*`, `*pingdom*`\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION + "\n" +
            "\n" +
            "NOTE: All errors that are captured during a request by an ignored user agent are still sent to the APM Server " +
            "regardless of this setting.")
        .dynamic(true)
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());

    private final ConfigurationOption<Boolean> usePathAsName = ConfigurationOption.booleanOption()
        .key("use_path_as_transaction_name")
        .configurationCategory(HTTP_CATEGORY)
        .tags("experimental")
        .description("If set to `true`,\n" +
            "transaction names of unsupported Servlet API-based frameworks will be in the form of `$method $path` instead of just `$method`.\n" +
            "\n" +
            "WARNING: If your URLs contain path parameters like `/user/$userId`,\n" +
            "you should be very careful when enabling this flag,\n" +
            "as it can lead to an explosion of transaction groups.\n" +
            "Take a look at the `url_groups` option on how to mitigate this problem by grouping URLs together.")
        .buildWithDefault(false);

    private final ConfigurationOption<List<WildcardMatcher>> urlGroups = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("url_groups")
        .configurationCategory(HTTP_CATEGORY)
        .description("This option is only considered, when `use_path_as_transaction_name` is active.\n" +
            "\n" +
            "With this option, you can group several URL paths together by using a wildcard expression like `/user/*`.\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());

    public EventType getCaptureBody() {
        return captureBody.get();
    }

    public List<WildcardMatcher> getIgnoreUrls() {
        return ignoreUrls.get();
    }

    public List<WildcardMatcher> getIgnoreUserAgents() {
        return ignoreUserAgents.get();
    }

    public boolean isUsePathAsName() {
        return usePathAsName.get();
    }

    public List<WildcardMatcher> getUrlGroups() {
        return urlGroups.get();
    }

    public boolean isCaptureHeaders() {
        return captureHeaders.get();
    }

    public enum EventType {
        /**
         * Request bodies will never be reported
         */
        OFF,
        /**
         * Request bodies will only be reported with errors
         */
        ERRORS,
        /**
         * Request bodies will only be reported with request transactions
         */
        TRANSACTIONS,
        /**
         * Request bodies will be reported with both errors and request transactions
         */
        ALL;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
