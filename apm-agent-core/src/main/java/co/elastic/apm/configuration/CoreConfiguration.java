/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

import co.elastic.apm.configuration.validation.RegexValidator;
import co.elastic.apm.matcher.WildcardMatcher;
import co.elastic.apm.matcher.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.ListValueConverter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CoreConfiguration extends ConfigurationOptionProvider {

    public static final String ACTIVE = "active";
    public static final String INSTRUMENT = "instrument";
    public static final String SERVICE_NAME = "service_name";
    public static final String SAMPLE_RATE = "transaction_sample_rate";
    private static final String CORE_CATEGORY = "Core";
    private final ConfigurationOption<Boolean> active = ConfigurationOption.booleanOption()
        .key(ACTIVE)
        .configurationCategory(CORE_CATEGORY)
        .description("A boolean specifying if the agent should be active or not. " +
            "If active, the agent will instrument incoming HTTP requests and track errors.\n" +
            "\n" +
            "You can use this setting to dynamically disable Elastic APM at runtime.")
        .dynamic(true)
        .buildWithDefault(true);

    private final ConfigurationOption<Boolean> instrument = ConfigurationOption.booleanOption()
        .key(INSTRUMENT)
        .configurationCategory(CORE_CATEGORY)
        .description("A boolean specifying if the agent should instrument the application to collect performance metrics for the app. " +
            "When set to false, Elastic APM will not affect your application at all.\n" +
            "\n" +
            "NOTE: Both active and instrument needs to be true for instrumentation to be running.")
        .buildWithDefault(true);

    private final ConfigurationOption<String> serviceName = ConfigurationOption.stringOption()
        .key(SERVICE_NAME)
        .configurationCategory(CORE_CATEGORY)
        .label("The name of your service (required)")
        .description("This is used to keep all the errors and transactions of your service together\n" +
            "and is the primary filter in the Elastic APM user interface.\n" +
            "\n" +
            "NOTE: The service name must conform to this regular expression: ^[a-zA-Z0-9 _-]+$. In less regexy terms: Your service name " +
            "must only contain characters from the ASCII alphabet, numbers, dashes, underscores and spaces.")
        .addValidator(RegexValidator.of("^[a-zA-Z0-9 _-]+$", "Your service name \"{0}\" must only contain characters " +
            "from the ASCII alphabet, numbers, dashes, underscores and spaces"))
        .buildRequired();

    private final ConfigurationOption<String> serviceVersion = ConfigurationOption.stringOption()
        .key("service_version")
        .configurationCategory(CORE_CATEGORY)
        .description("A version string for the currently deployed version of the service. If you don’t version your deployments, " +
            "the recommended value for this field is the commit identifier of the deployed revision, " +
            "e.g. the output of git rev-parse HEAD.")
        .build();

    private final ConfigurationOption<String> environment = ConfigurationOption.stringOption()
        .key("environment")
        .configurationCategory(CORE_CATEGORY)
        .description("The name of the environment this service is deployed in, e.g. \"production\" or \"staging\".")
        .build();

    private final ConfigurationOption<Double> sampleRate = ConfigurationOption.doubleOption()
        .key(SAMPLE_RATE)
        .aliasKeys("sample_rate")
        .configurationCategory(CORE_CATEGORY)
        .description("By default, the agent will sample every transaction (e.g. request to your service). " +
            "To reduce overhead and storage requirements, you can set the sample rate to a value between 0.0 and 1.0. " +
            "We still record overall time and the result for unsampled transactions, but no context information, tags, or spans.")
        .dynamic(true)
        .addValidator(new ConfigurationOption.Validator<Double>() {
            @Override
            public void assertValid(Double value) {
                if (value != null) {
                    if (value < 0 || value > 1) {
                        throw new IllegalArgumentException("The sample rate must be between 0 and 1");
                    }
                }
            }
        })
        .buildWithDefault(1.0);

    private final ConfigurationOption<Integer> transactionMaxSpans = ConfigurationOption.integerOption()
        .key("transaction_max_spans")
        .configurationCategory(CORE_CATEGORY)
        .description("Limits the amount of spans that are recorded per transaction.\n\n" +
            "This is helpful in cases where a transaction creates a very high amount of spans (e.g. thousands of SQL queries).\n\n" +
            "Setting an upper limit will prevent overloading the agent and the APM server with too much work for such edge cases.")
        .dynamic(true)
        .buildWithDefault(500);

    private final ConfigurationOption<List<WildcardMatcher>> sanitizeFieldNames = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("sanitize_field_names")
        .configurationCategory(CORE_CATEGORY)
        .description("Sometimes it is necessary to sanitize the data sent to Elastic APM,\n" +
            "e.g. remove sensitive data.\n" +
            "\n" +
            "Configure a list of wildcard patterns of field names which should be sanitized.\n" +
            "These apply for example to HTTP headers and `application/x-www-form-urlencoded` data.\n" +
            "\n" +
            "Entries can have a wildcard at the beginning and at the end.\n" +
            "Prepending an element with `(?i)` makes the matching case-insensitive.\n" +
            "\n" +
            "NOTE: Data in the query string is considered non-sensitive,\n" +
            "as sensitive information should not be sent in the query string.\n" +
            "See https://www.owasp.org/index.php/Information_exposure_through_query_strings_in_url for more information\n" +
            "\n" +
            "NOTE: Review the data captured by Elastic APM carefully to make sure it does not capture sensitive information.\n" +
            "If you do find sensitive data in the Elasticsearch index,\n" +
            "you should add an additional entry to this list (make sure to also include the default entries)."
            /* A disadvantage of this approach is when a user adds a custom value,
             * they don't automatically pick up new default values.
             * But the possibility to remove default values which are leading to false positive for the user
             * outweights this disadvantage imho.
             * Another advantage of having a configuration option vs offering a Filter or Processor in the public API is
             * that we don't have to expose the internal data format to the public API.
             */
        )
        .dynamic(true)
        .tags("security")
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.valueOf("(?i)password"),
            WildcardMatcher.valueOf("(?i)passwd"),
            WildcardMatcher.valueOf("(?i)pwd"),
            WildcardMatcher.valueOf("(?i)secret"),
            WildcardMatcher.valueOf("(?i)token"),
            WildcardMatcher.valueOf("(?i)*key"),
            WildcardMatcher.valueOf("(?i)*token"),
            WildcardMatcher.valueOf("(?i)*session*"),
            WildcardMatcher.valueOf("(?i)*credit*"),
            WildcardMatcher.valueOf("(?i)*card*"),
            // HTTP request header for basic auth, contains passwords
            WildcardMatcher.valueOf("(?i)authorization"),
            // HTTP response header which can contain session ids
            WildcardMatcher.valueOf("(?i)set-cookie")
        ));

    private final ConfigurationOption<Boolean> distributedTracing = ConfigurationOption.booleanOption()
        .key("distributed_tracing")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("Enables distributed tracing and uses the updated json schema to serialize payloads, transactions and spans")
        .buildWithDefault(false);

    private final ConfigurationOption<Collection<String>> disabledInstrumentations = ConfigurationOption.stringsOption()
        .key("disable_instrumentations")
        .aliasKeys("disabled_instrumentations")
        .configurationCategory(CORE_CATEGORY)
        .description("A list of instrumentations which should be disabled.\n" +
            "Valid options are `jdbc`, `servlet-api`, `servlet-api-async`, `spring-mvc`, `http-client`, `apache-httpclient`," +
            "`spring-resttemplate` and `incubating`.\n" +
            "If you want to try out incubating features,\n" +
            "set the value to an empty string.")
        .buildWithDefault(Collections.<String>singleton("incubating"));

    public boolean isActive() {
        return active.get();
    }

    public boolean isInstrument() {
        return instrument.get();
    }

    public String getServiceName() {
        return serviceName.get();
    }

    public String getServiceVersion() {
        return serviceVersion.get();
    }

    public String getEnvironment() {
        return environment.get();
    }

    public ConfigurationOption<Double> getSampleRate() {
        return sampleRate;
    }

    public int getTransactionMaxSpans() {
        return transactionMaxSpans.get();
    }

    public List<WildcardMatcher> getSanitizeFieldNames() {
        return sanitizeFieldNames.get();
    }

    public boolean isDistributedTracingEnabled() {
        return distributedTracing.get();
    }

    public Collection<String> getDisabledInstrumentations() {
        return disabledInstrumentations.get();
    }
}
