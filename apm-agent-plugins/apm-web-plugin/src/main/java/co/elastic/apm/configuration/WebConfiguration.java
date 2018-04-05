/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.configuration;

import co.elastic.apm.matcher.WildcardMatcher;
import co.elastic.apm.matcher.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.ListValueConverter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class WebConfiguration extends ConfigurationOptionProvider {

    private static final String HTTP_CATEGORY = "HTTP";
    private final ConfigurationOption<EventType> captureBody = ConfigurationOption.enumOption(EventType.class)
        .key("capture_body")
        .configurationCategory(HTTP_CATEGORY)
        .description("For transactions that are HTTP requests, the Java agent can optionally capture the request body (e.g. POST " +
            "variables).\n" +
            "\n" +
            "Possible values: errors, transactions, all, off.\n" +
            "\n" +
            "If the request has a body and this setting is disabled, the body will be shown as [REDACTED].\n" +
            "\n" +
            "For requests with a content type of multipart/form-data, any uploaded files will be referenced in a special _files key. It " +
            "contains the name of the field, and the name of the uploaded file, if provided.\n" +
            "\n" +
            "WARNING: request bodies often contain sensitive values like passwords, credit card numbers etc." +
            "If your service handles data like this, we advise to only enable this feature with care.")
        .dynamic(true)
        .buildWithDefault(EventType.OFF);

    private final ConfigurationOption<List<WildcardMatcher>> ignoreUrls = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("ignore_urls")
        .configurationCategory(HTTP_CATEGORY)
        .description("Used to restrict requests to certain URLs from being instrumented.\n" +
            "\n" +
            "This property should be set to an array containing one or more strings.\n" +
            "When an incoming HTTP request is detected, its URL will be tested against each element in this list.\n" +
            "Entries can have a wildcard at the beginning and at the end.\n" +
            "Example: `/resources/*, *.js, *static*`\n" +
            "\n" +
            "NOTE: All errors that are captured during a request to an ignored URL are still sent to the APM Server regardless of " +
            "this setting.")
        .dynamic(true)
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());
    private final ConfigurationOption<List<WildcardMatcher>> ignoreUserAgents = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("ignore_user_agents")
        .configurationCategory(HTTP_CATEGORY)
        .description("Used to restrict requests from certain User-Agents from being instrumented.\n" +
            "\n" +
            "When an incoming HTTP request is detected,\n" +
            "the User-Agent from the request headers will be tested against each element in this list.\n" +
            "Entries can have a wildcard at the beginning and at the end.\n" +
            "Example: `curl/*, *pingdom*`\n" +
            "\n" +
            "NOTE: All errors that are captured during a request by an ignored user agent are still sent to the APM Server " +
            "regardless of this setting.")
        .dynamic(true)
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());

    public EventType getCaptureBody() {
        return captureBody.get();
    }

    public Collection<WildcardMatcher> getIgnoreUrls() {
        return ignoreUrls.get();
    }

    public Collection<WildcardMatcher> getIgnoreUserAgents() {
        return ignoreUserAgents.get();
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
        ALL
    }
}
