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
package co.elastic.apm.agent.tracer.configuration;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.ListValueConverter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static co.elastic.apm.agent.tracer.configuration.RangeValidator.isInRange;

public class WebConfiguration extends ConfigurationOptionProvider {

    private static final String HTTP_CATEGORY = "HTTP";

    private final ConfigurationOption<List<WildcardMatcher>> captureContentTypes = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("capture_body_content_types")
        .configurationCategory(HTTP_CATEGORY)
        .tags("added[1.5.0]", "performance")
        .description("Configures which content types should be recorded.\n" +
            "\n" +
            "The defaults end with a wildcard so that content types like `text/plain; charset=utf-8` are captured as well.\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.valueOf("application/x-www-form-urlencoded*"),
            WildcardMatcher.valueOf("text/*"),
            WildcardMatcher.valueOf("application/json*"),
            WildcardMatcher.valueOf("application/xml*")
        ));

    private final ConfigurationOption<List<WildcardMatcher>> ignoreUrls = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("transaction_ignore_urls")
        .aliasKeys("ignore_urls")
        .configurationCategory(HTTP_CATEGORY)
        .description("Used to restrict requests to certain URLs from being instrumented.\n" +
            "\n" +
            "This property should be set to an array containing one or more strings.\n" +
            "When an incoming HTTP request is detected, its URL will be tested against each element in this list.\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
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
        .key("transaction_ignore_user_agents")
        .aliasKeys("ignore_user_agents")
        .tags("added[1.22.0]")
        .configurationCategory(HTTP_CATEGORY)
        .description("Used to restrict requests from certain User-Agents from being instrumented.\n" +
            "\n" +
            "When an incoming HTTP request is detected,\n" +
            "the User-Agent from the request headers will be tested against each element in this list.\n" +
            "Example: `curl/*`, `*pingdom*`\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());

    private final ConfigurationOption<Boolean> usePathAsName = ConfigurationOption.booleanOption()
        .key("use_path_as_transaction_name")
        .configurationCategory(HTTP_CATEGORY)
        .tags("added[1.0.0,Changing this value at runtime is possible since version 1.22.0]")
        .description("If set to `true`,\n" +
            "transaction names of unsupported or partially-supported frameworks will be in the form of `$method $path` instead of just `$method unknown route`.\n" +
            "\n" +
            "WARNING: If your URLs contain path parameters like `/user/$userId`,\n" +
            "you should be very careful when enabling this flag,\n" +
            "as it can lead to an explosion of transaction groups.\n" +
            "Take a look at the <<config-transaction-name-groups,`transaction_name_groups`>> option on how to mitigate this problem by grouping URLs together.")
        .dynamic(true)
        .buildWithDefault(false);

    private final ConfigurationOption<List<WildcardMatcher>> urlGroups = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("url_groups")
        .tags("deprecated")
        .configurationCategory(HTTP_CATEGORY)
        .description("Deprecated in favor of <<config-transaction-name-groups,`transaction_name_groups`>>.\n" +
            "\n" +
            "This option is only considered, when `use_path_as_transaction_name` is active.\n" +
            "\n" +
            "With this option, you can group several URL paths together by using a wildcard expression like `/user/*`.\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());

    private final ConfigurationOption<Integer> captureClientRequestBytes = ConfigurationOption.integerOption()
        .addValidator(isInRange(0, Integer.MAX_VALUE))
        .key("capture_http_client_request_body_size")
        .configurationCategory(HTTP_CATEGORY)
        .tags("added[1.52.0]", "experimental")
        .description("Configures that the first n bytes of http-client request bodies shall be captured. " +
                     "Note that only request bodies will be captured for content types matching the <<config-transaction-name-groups,`transaction_name_groups`>> configuration. " +
                     "A value of 0 disables body capturing. Note that even if this option is configured higher, the maximum amount of decoded characters will still be limited by the value of the <<config-long-field-max-length, `long_field_max_length`>> option.\n\n" +
                     "Currently only support for Apache Http Client v4 and v5, HttpUrlConnection, Spring Webflux WebClient and other frameworks building on top of these (e.g. Spring RestTemplate).")
        .dynamic(true)
        .buildWithDefault(0);

    private final ConfigurationOption<Boolean> captureClientRequestBodyAsLabel = ConfigurationOption.booleanOption()
        .key("capture_http_client_request_body_as_label")
        .configurationCategory(HTTP_CATEGORY)
        .tags("added[1.54.0]", "experimental")
        .description("TBD")
        .dynamic(false)
        .buildWithDefault(false);

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

    public List<WildcardMatcher> getCaptureContentTypes() {
        return captureContentTypes.get();
    }

    public int getCaptureClientRequestBytes() {
        return captureClientRequestBytes.get();
    }

    public boolean isCaptureClientRequestBodyAsLabel() {
        return captureClientRequestBodyAsLabel.get();
    }

}
