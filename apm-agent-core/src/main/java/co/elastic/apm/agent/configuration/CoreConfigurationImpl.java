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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.matcher.MethodMatcher;
import co.elastic.apm.agent.matcher.MethodMatcherValueConverter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.configuration.CoreConfiguration;
import co.elastic.apm.agent.tracer.configuration.ListValueConverter;
import co.elastic.apm.agent.tracer.configuration.RegexValidator;
import co.elastic.apm.agent.tracer.configuration.RoundedDoubleConverter;
import co.elastic.apm.agent.tracer.configuration.TimeDuration;
import co.elastic.apm.agent.tracer.configuration.TimeDurationValueConverter;
import co.elastic.apm.agent.tracer.configuration.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.AbstractValueConverter;
import org.stagemonitor.configuration.converter.MapValueConverter;
import org.stagemonitor.configuration.converter.SetValueConverter;
import org.stagemonitor.configuration.converter.StringValueConverter;
import org.stagemonitor.configuration.converter.ValueConverter;
import org.stagemonitor.configuration.source.ConfigurationSource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static co.elastic.apm.agent.logging.LoggingConfigurationImpl.AGENT_HOME_PLACEHOLDER;
import static co.elastic.apm.agent.tracer.configuration.RangeValidator.isInRange;

public class CoreConfigurationImpl extends ConfigurationOptionProvider implements CoreConfiguration {

    public static final int DEFAULT_LONG_FIELD_MAX_LENGTH = 10000;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String INSTRUMENT = "instrument";
    public static final String INSTRUMENT_ANCIENT_BYTECODE = "instrument_ancient_bytecode";
    public static final String SERVICE_NAME = "service_name";
    public static final String SERVICE_NODE_NAME = "service_node_name";
    public static final String SAMPLE_RATE = "transaction_sample_rate";
    public static final String CORE_CATEGORY = "Core";
    private static final String DEFAULT_CONFIG_FILE = AGENT_HOME_PLACEHOLDER + "/elasticapm.properties";
    public static final String CONFIG_FILE = "config_file";
    public static final String ENABLED_KEY = "enabled";

    private final ConfigurationOption<Boolean> enabled = ConfigurationOption.booleanOption()
        .key(ENABLED_KEY)
        .configurationCategory(CORE_CATEGORY)
        .description("Setting to false will completely disable the agent, including instrumentation and remote config polling.\n" +
            "If you want to dynamically change the status of the agent, use <<config-recording,`recording`>> instead.")
        .dynamic(false)
        .tags("added[1.18.0]")
        .buildWithDefault(true);

    private final ConfigurationOption<Boolean> instrument = ConfigurationOption.booleanOption()
        .key(INSTRUMENT)
        .configurationCategory(CORE_CATEGORY)
        .description("A boolean specifying if the agent should instrument the application to collect traces for the app.\n " +
            "When set to `false`, most built-in instrumentation plugins are disabled, which would minimize the effect on \n" +
            "your application. However, the agent would still apply instrumentation related to manual tracing options and it \n" +
            "would still collect and send metrics to APM Server.\n" +
            "\n" +
            "NOTE: Both active and instrument needs to be true for instrumentation to be running.\n" +
            "\n" +
            "NOTE: Changing this value at runtime can slow down the application temporarily.")
        .dynamic(true)
        .tags("added[1.0.0,Changing this value at runtime is possible since version 1.15.0]")
        .buildWithDefault(true);

    private final ConfigurationOption<String> serviceName = ConfigurationOption.stringOption()
        .key(SERVICE_NAME)
        .configurationCategory(CORE_CATEGORY)
        .label("The name of your service")
        .description("This is used to keep all the errors and transactions of your service together\n" +
            "and is the primary filter in the Elastic APM user interface.\n" +
            "\n" +
            "Instead of configuring the service name manually,\n" +
            "you can also choose to rely on the service name auto-detection mechanisms of the agent.\n" +
            "If `service_name` is set explicitly, all auto-detection mechanisms are disabled.\n" +
            "\n" +
            "This is how the service name auto-detection works:\n" +
            "\n" +
            "* For standalone applications\n" +
            "** The agent uses `Implementation-Title` in the `META-INF/MANIFEST.MF` file if the application is started via `java -jar`.\n" +
            "** Falls back to the name of the main class or jar file.\n" +
            "* For applications that are deployed to a servlet container/application server, the agent auto-detects the name for each application.\n" +
            "** For Spring-based applications, the agent uses the `spring.application.name` property, if set.\n" +
            "** For servlet-based applications, falls back to the `Implementation-Title` in the `META-INF/MANIFEST.MF` file.\n" +
            "** Falls back to the `display-name` of the `web.xml`, if available.\n" +
            "** Falls back to the servlet context path the application is mapped to (unless mapped to the root context).\n" +
            "\n" +
            "Generally, it is recommended to rely on the service name detection based on `META-INF/MANIFEST.MF`.\n" +
            "Spring Boot automatically adds the relevant manifest entries.\n" +
            "For other applications that are built with Maven, this is how you add the manifest entries:\n" +
            "\n" +
            "<#noparse>\n" +
            "[source,xml]\n" +
            ".pom.xml\n" +
            "----\n" +
            "    <build>\n" +
            "        <plugins>\n" +
            "            <plugin>\n" +
            "                <!-- replace with 'maven-war-plugin' if you're building a war -->\n" +
            "                <artifactId>maven-jar-plugin</artifactId>\n" +
            "                <configuration>\n" +
            "                    <archive>\n" +
            "                        <!-- Adds\n" +
            "                        Implementation-Title based on ${project.name} and\n" +
            "                        Implementation-Version based on ${project.version}\n" +
            "                        -->\n" +
            "                        <manifest>\n" +
            "                            <addDefaultImplementationEntries>true</addDefaultImplementationEntries>\n" +
            "                        </manifest>\n" +
            "                        <!-- To customize the Implementation-* entries, remove addDefaultImplementationEntries and add them manually\n" +
            "                        <manifestEntries>\n" +
            "                            <Implementation-Title>foo</Implementation-Title>\n" +
            "                            <Implementation-Version>4.2.0</Implementation-Version>\n" +
            "                        </manifestEntries>\n" +
            "                        -->\n" +
            "                    </archive>\n" +
            "                </configuration>\n" +
            "            </plugin>\n" +
            "        </plugins>\n" +
            "    </build>\n" +
            "----\n" +
            "</#noparse>\n" +
            "\n" +
            "The service name must conform to this regular expression: `^[a-zA-Z0-9 _-]+$`.\n" +
            "In less regexy terms:\n" +
            "Your service name must only contain characters from the ASCII alphabet, numbers, dashes, underscores and spaces.\n" +
            "\n" +
            "NOTE: Service name auto discovery mechanisms require APM Server 7.0+.")
        .addValidator(RegexValidator.of("^[a-zA-Z0-9 _-]+$", "Your service name \"{0}\" must only contain characters " +
            "from the ASCII alphabet, numbers, dashes, underscores and spaces"))
        .buildWithDefault(AutoDetectedServiceInfo.autoDetected().getServiceName());

    private final ConfigurationOption<String> serviceNodeName = ConfigurationOption.stringOption()
        .key(SERVICE_NODE_NAME)
        .configurationCategory(CORE_CATEGORY)
        .label("A unique name for the service node")
        .description("If set, this name is used to distinguish between different nodes of a service, \n" +
            "therefore it should be unique for each JVM within a service. \n" +
            "If not set, data aggregations will be done based on a container ID (where valid) or on the reported \n" +
            "hostname (automatically discovered or manually configured through <<config-hostname, `hostname`>>). \n" +
            "\n" +
            "NOTE: JVM metrics views rely on aggregations that are based on the service node name. \n" +
            "If you have multiple JVMs installed on the same host reporting data for the same service name, \n" +
            "you must set a unique node name for each in order to view metrics at the JVM level.\n" +
            "\n" +
            "NOTE: Metrics views can utilize this configuration since APM Server 7.5")
        .tags("added[1.11.0]")
        .build();

    private final ConfigurationOption<TimeDuration> delayTracerStart = TimeDurationValueConverter.durationOption("ms")
        .key("delay_tracer_start")
        // supporting the older name for backward compatibility
        .aliasKeys("delay_initialization")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("If set to a value greater than 0ms, the agent will delay tracer start. Instrumentation will not be delayed, " +
            "as well as some tracer initialization processes, like LifecycleListeners initializations.")
        .buildWithDefault(TimeDuration.of("0ms"));

    private final ConfigurationOption<String> serviceVersion = ConfigurationOption.stringOption()
        .key("service_version")
        .configurationCategory(CORE_CATEGORY)
        .description("A version string for the currently deployed version of the service. If you don’t version your deployments, " +
            "the recommended value for this field is the commit identifier of the deployed revision, " +
            "e.g. the output of git rev-parse HEAD.\n" +
            "\n" +
            "Similar to the auto-detection of <<config-service-name>>, " +
            "the agent can auto-detect the service version based on the `Implementation-Title` attribute in `META-INF/MANIFEST.MF`.\n" +
            "See <<config-service-name>> on how to set this attribute.\n" +
            "\n")
        .defaultValue(AutoDetectedServiceInfo.autoDetected().getServiceVersion())
        .build();

    private final ConfigurationOption<String> hostname = ConfigurationOption.stringOption()
        .key("hostname")
        .tags("added[1.10.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("Allows for the reported hostname to be manually specified. If unset the hostname will be looked up.")
        .build();

    private final ConfigurationOption<String> environment = ConfigurationOption.stringOption()
        .key("environment")
        .configurationCategory(CORE_CATEGORY)
        .description("The name of the environment this service is deployed in, e.g. \"production\" or \"staging\".\n" +
            "\n" +
            "Environments allow you to easily filter data on a global level in the APM app.\n" +
            "It's important to be consistent when naming environments across agents.\n" +
            "See {apm-app-ref}/filters.html#environment-selector[environment selector] in the APM app for more information.\n\n" +
            "NOTE: This feature is fully supported in the APM app in Kibana versions >= 7.2.\n" +
            "You must use the query bar to filter for a specific environment in versions prior to 7.2.")
        .build();

    private final ConfigurationOption<Double> sampleRate = ConfigurationOption.builder(RoundedDoubleConverter.withDefaultPrecision(), Double.class)
        .key(SAMPLE_RATE)
        .aliasKeys("sample_rate")
        .configurationCategory(CORE_CATEGORY)
        .tags("performance")
        .description("By default, the agent will sample every transaction (e.g. request to your service). " +
            "To reduce overhead and storage requirements, you can set the sample rate to a value between 0.0 and 1.0. " +
            "(For pre-8.0 servers the agent still records and sends overall time and the result for unsampled transactions, but no context information, labels, or spans." +
            " When connecting to 8.0+ servers, the unsampled requests are not sent at all).\n\n" +
            "Value will be rounded with 4 significant digits, as an example, value '0.55555' will be rounded to `0.5556`")
        .dynamic(true)
        .addValidator(isInRange(0d, 1d))
        .buildWithDefault(1.0);

    private final ConfigurationOption<Integer> transactionMaxSpans = ConfigurationOption.integerOption()
        .key("transaction_max_spans")
        .configurationCategory(CORE_CATEGORY)
        .tags("performance")
        .description("Limits the amount of spans that are recorded per transaction.\n\n" +
            "This is helpful in cases where a transaction creates a very high amount of spans (e.g. thousands of SQL queries).\n\n" +
            "Setting an upper limit will prevent overloading the agent and the APM server with too much work for such edge cases.\n\n" +
            "A message will be logged when the max number of spans has been exceeded but only at a rate of once every " + TimeUnit.MICROSECONDS.toMinutes(SpanImpl.MAX_LOG_INTERVAL_MICRO_SECS) + " minutes to ensure performance is not impacted.")
        .dynamic(true)
        .buildWithDefault(500);

    private final ConfigurationOption<Integer> longFieldMaxLength = ConfigurationOption.integerOption()
        .key("long_field_max_length")
        .configurationCategory(CORE_CATEGORY)
        .tags("performance", "added[1.37.0]")
        .description("\n" +
            "The following transaction, span, and error fields will be truncated at this number of unicode characters " +
            "before being sent to APM server:\n\n" +
            "- `transaction.context.request.body`, `error.context.request.body`\n" +
            "- `transaction.context.message.body`, `error.context.message.body`\n" +
            "- `span.context.db.statement`\n" +
            "\nNote that tracing data is limited at the upstream APM server to \n" +
            "{apm-guide-ref}/configuration-process.html#max_event_size[`max_event_size`], \n" +
            "which defaults to 300kB. If you configure `long_field_max_length` too large, it \n" +
            "could result in transactions, spans, or errors that are rejected by APM server.")
        .dynamic(false)
        .buildWithDefault(DEFAULT_LONG_FIELD_MAX_LENGTH);

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
                WildcardMatcher.DOCUMENTATION + "\n" +
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
            WildcardMatcher.valueOf("password"),
            WildcardMatcher.valueOf("passwd"),
            WildcardMatcher.valueOf("pwd"),
            WildcardMatcher.valueOf("secret"),
            WildcardMatcher.valueOf("*key"),
            WildcardMatcher.valueOf("*token*"),
            WildcardMatcher.valueOf("*session*"),
            WildcardMatcher.valueOf("*credit*"),
            WildcardMatcher.valueOf("*card*"),
            WildcardMatcher.valueOf("*auth*"),
            WildcardMatcher.valueOf("*principal*"),
            // HTTP response header which can contain session ids
            WildcardMatcher.valueOf("set-cookie")
        ));

    private final ConfigurationOption<Collection<String>> enabledInstrumentations = ConfigurationOption.stringsOption()
        .key("enable_instrumentations")
        .configurationCategory(CORE_CATEGORY)
        .description("A list of instrumentations which should be selectively enabled.\n" +
            "Valid options are ${allInstrumentationGroupNames}.\n" +
            "When set to non-empty value, only listed instrumentations will be enabled if they are not disabled through <<config-disable-instrumentations>> or <<config-enable-experimental-instrumentations>>.\n" +
            "When not set or empty (default), all instrumentations enabled by default will be enabled unless they are disabled through <<config-disable-instrumentations>> or <<config-enable-experimental-instrumentations>>.\n" +
            "\n" +
            "NOTE: Changing this value at runtime can slow down the application temporarily.")
        .dynamic(true)
        .tags("added[1.28.0]")
        .buildWithDefault(Collections.<String>emptyList());

    private final ConfigurationOption<Collection<String>> disabledInstrumentations = ConfigurationOption.builder(new AbstractValueConverter<Collection<String>>() {
            @Override
            public Collection<String> convert(String s) {
                Collection<String> values = SetValueConverter.STRINGS_VALUE_CONVERTER.convert(s);
                if (values.contains("incubating")) {
                    Set<String> legacyValues = new LinkedHashSet<String>(values);
                    legacyValues.add("experimental");

                    return Collections.unmodifiableSet(legacyValues);
                }

                return values;
            }

            @Override
            public String toString(Collection<String> value) {
                return SetValueConverter.STRINGS_VALUE_CONVERTER.toString(value);
            }
        }, Collection.class)
        .key("disable_instrumentations")
        .aliasKeys("disabled_instrumentations")
        .configurationCategory(CORE_CATEGORY)
        .description("A list of instrumentations which should be disabled.\n" +
            "Valid options are ${allInstrumentationGroupNames}.\n" +
            "For version `1.25.0` and later, use <<config-enable-experimental-instrumentations>> to enable experimental instrumentations.\n" +
            "\n" +
            "NOTE: Changing this value at runtime can slow down the application temporarily.")
        .dynamic(true)
        .tags("added[1.0.0,Changing this value at runtime is possible since version 1.15.0]")
        .buildWithDefault(Collections.<String>emptyList());

    private final ConfigurationOption<Boolean> enableExperimentalInstrumentations = ConfigurationOption.booleanOption()
        .key("enable_experimental_instrumentations")
        .configurationCategory(CORE_CATEGORY)
        .description("Whether to apply experimental instrumentations.\n" +
            "\n" +
            "NOTE: Changing this value at runtime can slow down the application temporarily." +
            "\n" +
            "Setting to `true` will enable instrumentations in the `experimental` group.")
        .dynamic(true)
        .tags("added[1.25.0]")
        .buildWithDefault(false);

    private final ConfigurationOption<List<WildcardMatcher>> unnestExceptions = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("unnest_exceptions")
        .configurationCategory(CORE_CATEGORY)
        .description("When reporting exceptions,\n" +
            "un-nests the exceptions matching the wildcard pattern.\n" +
            "This can come in handy for Spring's `org.springframework.web.util.NestedServletException`,\n" +
            "for example.\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .buildWithDefault(Collections.singletonList(WildcardMatcher.valueOf("(?-i)*Nested*Exception")));

    private final ConfigurationOption<List<WildcardMatcher>> ignoreExceptions = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("ignore_exceptions")
        .tags("added[1.11.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("A list of exceptions that should be ignored and not reported as errors.\n" +
            "This allows to ignore exceptions thrown in regular control flow that are not actual errors\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION + "\n" +
            "\n" +
            "Examples:\n" +
            "\n" +
            " - `com.mycompany.ExceptionToIgnore`: using fully qualified name\n" +
            " - `*ExceptionToIgnore`: using wildcard to avoid package name\n" +
            " - `*exceptiontoignore`: case-insensitive by default\n" +
            "\n" +
            "NOTE: Exception inheritance is not supported, thus you have to explicitly list all the thrown exception types"
        )
        .dynamic(true)
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());

    private final ConfigurationOption<Boolean> captureExceptionDetails = ConfigurationOption.<Boolean>booleanOption()
        .key("capture_exception_details")
        .tags("internal")
        .configurationCategory(CORE_CATEGORY)
        .dynamic(true)
        .buildWithDefault(true);

    private final ConfigurationOption<EventType> captureBody = ConfigurationOption.enumOption(EventType.class)
        .key("capture_body")
        .configurationCategory(CORE_CATEGORY)
        .tags("performance")
        .description("For transactions that are HTTP requests, the Java agent can optionally capture the request body (e.g. POST \n" +
            "variables). For transactions that are initiated by receiving a message from a message broker, the agent can \n" +
            "capture the textual message body.\n" +
            "\n" +
            "If the HTTP request or the message has a body and this setting is disabled, the body will be shown as [REDACTED].\n" +
            "\n" +
            "This option is case-insensitive.\n" +
            "\n" +
            "NOTE: Currently, the body length is limited to 10000 characters and it is not configurable. \n" +
            "If the body size exceeds the limit, it will be truncated. \n" +
            "\n" +
            "NOTE: Currently, only UTF-8 encoded plain text HTTP content types are supported.\n" +
            "The option <<config-capture-body-content-types>> determines which content types are captured.\n" +
            "\n" +
            "WARNING: Request bodies often contain sensitive values like passwords, credit card numbers etc.\n" +
            "If your service handles data like this, we advise to only enable this feature with care.\n" +
            "Turning on body capturing can also significantly increase the overhead in terms of heap usage,\n" +
            "network utilisation and Elasticsearch index size.")
        .dynamic(true)
        .buildWithDefault(EventType.OFF);

    private final ConfigurationOption<Boolean> captureHeaders = ConfigurationOption.booleanOption()
        .key("capture_headers")
        .configurationCategory(CORE_CATEGORY)
        .tags("performance")
        .description("If set to `true`, the agent will capture HTTP request and response headers (including cookies), \n" +
            "as well as messages' headers/properties when using messaging frameworks like Kafka or JMS.\n" +
            "\n" +
            "NOTE: Setting this to `false` reduces network bandwidth, disk space and object allocations.")
        .dynamic(true)
        .buildWithDefault(true);

    private final ConfigurationOption<Map<String, String>> globalLabels = ConfigurationOption
        .builder(new MapValueConverter<String, String>(StringValueConverter.INSTANCE, StringValueConverter.INSTANCE, "=", ","), Map.class)
        .key("global_labels")
        .tags("added[1.7.0, Requires APM Server 7.2+]")
        .configurationCategory(CORE_CATEGORY)
        .description("Labels added to all events, with the format `key=value[,key=value[,...]]`.\n" +
            "Any labels set by application via the API will override global labels with the same keys.\n" +
            "\n" +
            "NOTE: This feature requires APM Server 7.2+")
        .dynamic(false)
        .buildWithDefault(Collections.<String, String>emptyMap());

    private final ConfigurationOption<Boolean> typePoolCache = ConfigurationOption.booleanOption()
        .key("enable_type_pool_cache")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("When enabled, configures Byte Buddy to use a type pool cache.")
        .buildWithDefault(true);

    private final ConfigurationOption<Boolean> instrumentAncientBytecode = ConfigurationOption.booleanOption()
        .key(INSTRUMENT_ANCIENT_BYTECODE)
        .configurationCategory(CORE_CATEGORY)
        .description("A boolean specifying if the agent should instrument pre-Java-1.4 bytecode.")
        .dynamic(false)
        .tags("added[1.35.0]")
        .buildWithDefault(false);

    private final ConfigurationOption<Boolean> warmupByteBuddy = ConfigurationOption.booleanOption()
        .key("warmup_byte_buddy")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("When set to true, configures Byte Buddy to warmup instrumentation processes on the \n" +
            "attaching thread just before installing the transformer on the JVM Instrumentation.")
        .buildWithDefault(true);

    private final ConfigurationOption<String> bytecodeDumpPath = ConfigurationOption.stringOption()
        .key("bytecode_dump_path")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("When set, the agent will create a directory in the provided path if such does not already " +
            "exist and use it to dump bytecode of instrumented classes.")
        .buildWithDefault("");

    private final ConfigurationOption<TimeDuration> threadDumpInterval = TimeDurationValueConverter.durationOption("ms")
        .key("thread_dump_interval")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("Triggers a thread dump at regular frequency, should be used to help configure trace_methods without knowledge of application code")
        .buildWithDefault(TimeDuration.of("0ms"));

    private final ConfigurationOption<Boolean> typeMatchingWithNamePreFilter = ConfigurationOption.booleanOption()
        .key("enable_type_matching_name_pre_filtering")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("When enabled, applies cheap name-based matchers to types before checking the type hierarchy.\n" +
            "This speeds up matching but can lead to false negatives,\n" +
            "for example when a javax.servlet.Servlet does not contain the word 'Servlet' in the class name.")
        .buildWithDefault(true);

    private final ConfigurationOption<Boolean> classLoadingMatchingPreFilter = ConfigurationOption.booleanOption()
        .key("enable_class_loading_pre_filtering")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("When enabled, applies class loader match before checking the type hierarchy that relies on CL cache.\n" +
            "This speeds up matching but can lead to class-loading-related side effects, for example when a class \n" +
            "is available somewhere in the classpath where it never gets loaded unless this matching is applied.")
        .buildWithDefault(true);

    private final ConfigurationOption<Boolean> contextPropagationOnly = ConfigurationOption.booleanOption()
        .key("context_propagation_only")
        .configurationCategory(CORE_CATEGORY)
        .description("When set to true, disables log sending, metrics and trace collection.\n" +
            "Trace context propagation and log correlation will stay active.\n"
        +"Note that in contrast to <<config-disable-send, `disable_send`>> the agent will still" +
            " connect to the APM-server for fetching configuration updates and health checks.")
        .dynamic(true)
        .tags("added[1.44.0]")
        .buildWithDefault(false);


    private final ConfigurationOption<Integer> safeExceptions = ConfigurationOption.<Boolean>integerOption()
        .key("safe_exceptions")
        .tags("internal")
        .configurationCategory(CORE_CATEGORY)
        .dynamic(true)
        .buildWithDefault(0);

    private final ConfigurationOption<List<WildcardMatcher>> classesExcludedFromInstrumentation = ConfigurationOption
        .builder(new ValueConverter<List<WildcardMatcher>>() {

            private final ValueConverter<List<WildcardMatcher>> delegate = new ListValueConverter<>(new WildcardMatcherValueConverter());

            @Override
            public List<WildcardMatcher> convert(String s) {
                List<WildcardMatcher> result = new ArrayList<>();
                for (WildcardMatcher matcher : delegate.convert(s)) {
                    if (matcher.matches("co.elastic.apm.")) {
                        logger.warn("Ignoring exclude '{}' for instrumentation because ignoring 'co.elastic.apm' can lead to unwanted side effects", matcher);
                    } else {
                        result.add(matcher);
                    }
                }
                return result;
            }

            @Override
            public String toString(List<WildcardMatcher> value) {
                return delegate.toString(value);
            }

            @Override
            public String toSafeString(List<WildcardMatcher> value) {
                return delegate.toSafeString(value);
            }

        }, List.class)
        .key("classes_excluded_from_instrumentation")
        .configurationCategory(CORE_CATEGORY)
        .description("Use to exclude specific classes from being instrumented. In order to exclude entire packages, \n" +
            "use wildcards, as in: `com.project.exclude.*`" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(false)
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());

    private final ConfigurationOption<List<WildcardMatcher>> defaultClassesExcludedFromInstrumentation = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("classes_excluded_from_instrumentation_default")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(false)
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.valueOf("(?-i)org.infinispan*"),
            WildcardMatcher.valueOf("(?-i)org.apache.xerces*"),
            WildcardMatcher.valueOf("(?-i)org.jboss.as.*"),
            WildcardMatcher.valueOf("(?-i)io.undertow.core*"),
            WildcardMatcher.valueOf("(?-i)org.eclipse.jdt.ecj*"),
            WildcardMatcher.valueOf("(?-i)org.wildfly.extension.*"),
            WildcardMatcher.valueOf("(?-i)org.wildfly.security*")
        ));

    private final ConfigurationOption<List<WildcardMatcher>> methodsExcludedFromInstrumentation = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("methods_excluded_from_instrumentation")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.valueOf("(?-i)_persistence_*")
        ));

    private final ConfigurationOption<List<MethodMatcher>> traceMethods = ConfigurationOption
        .builder(MethodMatcherValueConverter.LIST, List.class)
        .key("trace_methods")
        .tags("added[1.3.0,Enhancements in 1.4.0, 1.7.0 and 1.9.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("A list of methods for which to create a transaction or span.\n" +
            "\n" +
            "If you want to monitor a large number of methods,\n" +
            "use  <<config-profiling-inferred-spans-enabled, `profiling_inferred_spans_enabled`>> instead.\n" +
            "\n" +
            "This works by instrumenting each matching method to include code that creates a span for the method.\n" +
            "While creating a span is quite cheap in terms of performance,\n" +
            "instrumenting a whole code base or a method which is executed in a tight loop leads to significant overhead.\n" +
            "\n" +
            "Using a pointcut-like syntax, you can match based on\n" +
            "\n" +
            " - Method modifier (optional) +\n" +
            "   Example: `public`, `protected`, `private` or `*`\n" +
            " - Package and class name (wildcards include sub-packages) +\n" +
            "   Example: `org.example.*`\n" +
            " - Method name (optional since 1.4.0) +\n" +
            "   Example: `myMeth*d`\n" +
            " - Method argument types (optional) +\n" +
            "   Example: `(*lang.String, int[])`\n" +
            " - Classes with a specific annotation (optional) +\n" +
            "   Example: `@*ApplicationScoped`\n" +
            " - Classes with a specific annotation that is itself annotated with the given meta-annotation (optional) +\n" +
            "   Example: `@@javax.enterpr*se.context.NormalScope`\n" +
            "\n" +
            "The syntax is `modifier @fully.qualified.AnnotationName fully.qualified.ClassName#methodName(fully.qualified.ParameterType)`.\n" +
            "\n" +
            "A few examples:\n" +
            "\n" +
            " - `org.example.*` Omitting the method is possible since 1.4.0\n" +
            " - `org.example.*#*` (before 1.4.0, you need to specify a method matcher)\n" +
            " - `org.example.MyClass#myMethod`\n" +
            " - `org.example.MyClass#myMethod()`\n" +
            " - `org.example.MyClass#myMethod(java.lang.String)`\n" +
            " - `org.example.MyClass#myMe*od(java.lang.String, int)`\n" +
            " - `private org.example.MyClass#myMe*od(java.lang.String, *)`\n" +
            " - `* org.example.MyClas*#myMe*od(*.String, int[])`\n" +
            " - `public org.example.services.*Service#*`\n" +
            " - `public @java.inject.ApplicationScoped org.example.*`\n" +
            " - `public @java.inject.* org.example.*`\n" +
            " - `public @@javax.enterprise.context.NormalScope org.example.*, public @@jakarta.enterprise.context.NormalScope org.example.*`\n" +
            "\n" +
            "NOTE: Only use wildcards if necessary.\n" +
            "The more methods you match the more overhead will be caused by the agent.\n" +
            "Also note that there is a maximum amount of spans per transaction (see <<config-transaction-max-spans, `transaction_max_spans`>>).\n" +
            "\n" +
            "NOTE: The agent will create stack traces for spans which took longer than\n" +
            "<<config-span-stack-trace-min-duration, `span_stack_trace_min_duration`>>.\n" +
            "When tracing a large number of methods (for example by using wildcards),\n" +
            "this may lead to high overhead.\n" +
            "Consider increasing the threshold or disabling stack trace collection altogether.\n\n" +
            "Common configurations:\n\n" +
            "Trace all public methods in CDI-Annotated beans:\n\n" +
            "----\n" +
            "public @@javax.enterprise.context.NormalScope your.application.package.*\n" +
            "public @@jakarta.enterprise.context.NormalScope your.application.package.*\n" +
            "public @@javax.inject.Scope your.application.package.*\n" +
            "----\n" +
            "NOTE: This method is only available in the Elastic APM Java Agent.\n" +
            "\n" +
            "NOTE: Changing this value at runtime can slow down the application temporarily.")
        .dynamic(true)
        .tags("added[1.0.0,Changing this value at runtime is possible since version 1.15.0]")
        .buildWithDefault(Collections.<MethodMatcher>emptyList());

    private final ConfigurationOption<TimeDuration> traceMethodsDurationThreshold = TimeDurationValueConverter.durationOption("ms")
        .key("trace_methods_duration_threshold")
        .tags("added[1.7.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("If <<config-trace-methods, `trace_methods`>> config option is set, provides a threshold to limit spans based on \n" +
            "duration. When set to a value greater than 0, spans representing methods traced based on `trace_methods` will be discarded " +
            "by default.\n" +
            "Such methods will be traced and reported if one of the following applies:\n" +
            "\n" +
            " - This method's duration crossed the configured threshold.\n" +
            " - This method ended with Exception.\n" +
            " - A method executed as part of the execution of this method crossed the threshold or ended with Exception.\n" +
            " - A \"forcibly-traced method\" (e.g. DB queries, HTTP exits, custom) was executed during the execution of this method.\n" +
            "\n" +
            "Set to 0 to disable.\n" +
            "\n" +
            "NOTE: Transactions are never discarded, regardless of their duration.\n" +
            "This configuration affects only spans.\n" +
            "In order not to break span references,\n" +
            "all spans leading to an async operation or an exit span (such as a HTTP request or a DB query) are never discarded,\n" +
            "regardless of their duration.\n" +
            "\n" +
            "NOTE: If this option and <<config-span-min-duration,`span_min_duration`>> are both configured,\n" +
            "the higher of both thresholds will determine which spans will be discarded.")
        .buildWithDefault(TimeDuration.of("0ms"));

    private final ConfigurationOption<Boolean> centralConfig = ConfigurationOption.booleanOption()
        .key("central_config")
        .tags("added[1.8.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("When enabled, the agent will make periodic requests to the APM Server to fetch updated configuration.\n"
                     + "The frequency of the periodic request is driven by the `Cache-Control` header returned from APM Server/Integration, falling back to 5 minutes if not defined.")
        .dynamic(true)
        .buildWithDefault(true);

    private final ConfigurationOption<Boolean> breakdownMetrics = ConfigurationOption.booleanOption()
        .key("breakdown_metrics")
        .tags("added[1.8.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("Disables the collection of breakdown metrics (`span.self_time`)")
        .buildWithDefault(true);

    private final ConfigurationOption<String> configFileLocation = ConfigurationOption.stringOption()
        .key(CONFIG_FILE)
        .tags("added[1.8.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("Sets the path of the agent config file.\n" +
            "The special value `_AGENT_HOME_` is a placeholder for the folder the `elastic-apm-agent.jar` is in.\n" +
            "The file has to be on the file system.\n" +
            "You can not refer to classpath locations.\n" +
            "\n" +
            "NOTE: this option can only be set via system properties, environment variables or the attacher options.")
        .buildWithDefault(DEFAULT_CONFIG_FILE);

    private final ConfigurationOption<String> pluginsDirLocation = ConfigurationOption.stringOption()
        .key("plugins_dir")
        .tags("added[1.18.0]")
        .configurationCategory(CORE_CATEGORY)
        .tags("experimental")
        .description("A folder that contains external agent plugins.\n" +
            "\n" +
            "Use the `apm-agent-plugin-sdk` and the `apm-agent-api` artifacts to create a jar and place it into the plugins folder.\n" +
            "The agent will load all instrumentations that are declared in the\n" +
            "`META-INF/services/co.elastic.apm.agent.sdk.ElasticApmInstrumentation` service descriptor.\n" +
            "See `integration-tests/external-plugin-test` for an example plugin.")
        .build();

    private final ConfigurationOption<Boolean> useElasticTraceparentHeader = ConfigurationOption.booleanOption()
        .key("use_elastic_traceparent_header")
        .tags("added[1.14.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("To enable {apm-guide-ref}/apm-distributed-tracing.html[distributed tracing], the agent\n" +
            "adds trace context headers to outgoing requests (like HTTP requests, Kafka records, gRPC requests etc.).\n" +
            "These headers (`traceparent` and `tracestate`) are defined in the\n" +
            "https://www.w3.org/TR/trace-context-1/[W3C Trace Context] specification.\n" +
            "\n" +
            "When this setting is `true`, the agent will also add the header `elastic-apm-traceparent`\n" +
            "for backwards compatibility with older versions of Elastic APM agents.")
        .dynamic(true)
        .buildWithDefault(true);

    private final ConfigurationOption<Boolean> disableOutgoingTraceContextHeaders = ConfigurationOption.booleanOption()
        .key("disable_outgoing_tracecontext_headers")
        .tags("added[1.37.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("Use this option to disable `tracecontext` headers injection to any outgoing communication. \n\n" +
            "NOTE: Disabling `tracecontext` headers injection means that {apm-guide-ref}/apm-distributed-tracing.html[distributed tracing] \n" +
            "will not work on downstream services.")
        .dynamic(true)
        .buildWithDefault(false);

    private final ConfigurationOption<Integer> tracestateHeaderSizeLimit = ConfigurationOption.integerOption()
        .key("tracestate_header_size_limit")
        .tags("added[1.14.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("The agent delegates the `tracestate` header, if received, as defined in the\n" +
            "https://www.w3.org/TR/trace-context-1/[W3C Trace Context] specification.\n" +
            "\n" +
            "This setting limits the size of the `tracestate` header.")
        .dynamic(true)
        .tags("internal")
        .buildWithDefault(4096);

    private final ConfigurationOption<TimeDuration> spanMinDuration = TimeDurationValueConverter.durationOption("ms")
        .key("span_min_duration")
        .tags("added[1.16.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("Sets the minimum duration of spans.\n" +
            "Spans that execute faster than this threshold are attempted to be discarded.\n" +
            "\n" +
            "The attempt fails if they lead up to a span that can't be discarded.\n" +
            "Spans that propagate the trace context to downstream services,\n" +
            "such as outgoing HTTP requests,\n" +
            "can't be discarded.\n" +
            "Additionally, spans that lead to an error or that may be a parent of an async operation can't be discarded.\n" +
            "\n" +
            "However, external calls that don't propagate context,\n" +
            "such as calls to a database, can be discarded using this threshold.")
        .dynamic(true)
        .buildWithDefault(TimeDuration.of("0ms"));

    private final ConfigurationOption<CloudProvider> cloudProvider = ConfigurationOption.enumOption(CloudProvider.class)
        .key("cloud_provider")
        .tags("added[1.21.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("This config value allows you to specify which cloud provider should be assumed \n" +
            "for metadata collection. By default, the agent will attempt to detect the cloud \n" +
            "provider or, if that fails, will use trial and error to collect the metadata.")
        .buildWithDefault(CloudProvider.AUTO);

    private final ConfigurationOption<TimeDuration> metadataTimeoutMs = TimeDurationValueConverter.durationOption("ms")
        .key("metadata_timeout_ms")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("Some metadata auto-discovery tasks require long execution. For example, cloud provider information \n" +
            "is fetched by querying APIs through HTTP and hostname discovery relies on running external commands. \n" +
            "In some cases, such discovery tasks rely on trial-and-error. This config option is used to limit the time \n" +
            "spent on metadata discovery. Wherever possible, these tasks are executed in parallel, but in some cases \n" +
            "they can't be, which means that this config doesn't indicate the absolute limit for the entire metadata \n" +
            "discovery. Rather, it defines the timeout for each metadata discovery task.")
        .buildWithDefault(TimeDuration.of("1000ms"));

    private final ConfigurationOption<Boolean> enablePublicApiAnnotationInheritance = ConfigurationOption.booleanOption()
        .key("enable_public_api_annotation_inheritance")
        .tags("added[1.25.0]")
        .configurationCategory(CORE_CATEGORY)
        .tags("performance")
        .description("A boolean specifying if the agent should search the class hierarchy for public api annotations (`@CaptureTransaction`, `@CaptureSpan`, `@Traced` and from 1.45.0 `@WithSpan`" +
            ").\n " +
            "When set to `false`, a method is instrumented if it is annotated with a public api annotation.\n  " +
            "When set to `true` methods overriding annotated methods will be instrumented as well.\n " +
            "Either way, methods will only be instrumented if they are included in the configured <<config-application-packages>>.")
        .dynamic(false)
        .buildWithDefault(false);

    private final ConfigurationOption<List<WildcardMatcher>> transactionNameGroups = ConfigurationOption
        .builder(new org.stagemonitor.configuration.converter.ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("transaction_name_groups")
        .tags("added[1.33.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("With this option,\n" +
            "you can group transaction names that contain dynamic parts with a wildcard expression.\n" +
            "For example,\n" +
            "the pattern `GET /user/*/cart` would consolidate transactions,\n" +
            "such as `GET /users/42/cart` and `GET /users/73/cart` into a single transaction name `GET /users/*/cart`,\n" +
            "hence reducing the transaction name cardinality.\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());

    private final ConfigurationOption<TraceContinuationStrategy> traceContinuationStrategy = ConfigurationOption.enumOption(TraceContinuationStrategy.class)
        .key("trace_continuation_strategy")
        .tags("added[1.34.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("This option allows some control over how the APM agent handles W3C trace-context headers on incoming requests. " +
            "By default, the `traceparent` and `tracestate` headers are used per W3C spec for distributed tracing. " +
            "However, in certain cases it can be helpful to not use the incoming `traceparent` header. Some example use cases:\n\n" +
            "* An Elastic-monitored service is receiving requests with `traceparent` headers from unmonitored services.\n" +
            "* An Elastic-monitored service is publicly exposed, and does not want tracing data (trace-ids, sampling decisions) to possibly be spoofed by user requests.\n\n" +
            "Valid values are:\n\n" +
            "* 'continue': The default behavior. An incoming `traceparent` value is used to continue the trace and determine the sampling decision.\n" +
            "* 'restart': Always ignores the `traceparent` header of incoming requests. A new trace-id will be generated and the sampling decision" +
            " will be made based on transaction_sample_rate. A span link will be made to the incoming `traceparent`.\n" +
            "* 'restart_external': If an incoming request includes the `es` vendor flag in `tracestate`, then any `traceparent` will be considered " +
            "internal and will be handled as described for 'continue' above. Otherwise, any `traceparent` is considered external and will be handled as described for 'restart' above.\n\n" +
            "Starting with Elastic Observability 8.2, span links are visible in trace views.\n\n" +
            "This option is case-insensitive.")
        .dynamic(true)
        .buildWithDefault(TraceContinuationStrategy.CONTINUE);

    private final ConfigurationOption<ActivationMethod> activationMethod = ConfigurationOption.enumOption(ActivationMethod.class)
        .key("activation_method")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("telling the agent what activated it, used for telemetry and should not be set unless supported by ActivationMethod")
        .dynamic(true)
        .buildWithDefault(ActivationMethod.UNKNOWN);


    private final ConfigurationOption<List<WildcardMatcher>> baggateToAttach = ConfigurationOption
        .builder(new org.stagemonitor.configuration.converter.ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("baggage_to_attach")
        .tags("added[1.43.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("If any baggage key matches any of the patterns provided via this config option," +
            " the corresponding baggage key and value will be automatically stored on the corresponding transactions, spans and errors." +
            " The baggage keys will be prefixed with \"baggage.\" on storage.")
        .dynamic(true)
        .buildWithDefault(Arrays.asList(WildcardMatcher.valueOf("*")));

    private final ConfigurationOption<Boolean> captureThreadOnStart = ConfigurationOption.booleanOption()
        .key("capture_thread_on_start")
        .configurationCategory(CORE_CATEGORY)
        .description("Whether to capture thread name and ID as labels.")
        .dynamic(true)
        .tags("internal")
        .buildWithDefault(false);

    public boolean isEnabled() {
        return enabled.get();
    }

    public boolean isInstrument() {
        return instrument.get();
    }

    public List<ConfigurationOption<?>> getInstrumentationOptions() {
        return Arrays.asList(instrument, traceMethods, enabledInstrumentations, disabledInstrumentations, enableExperimentalInstrumentations);
    }

    @Override
    public String getServiceName() {
        return serviceName.get();
    }

    public ConfigurationOption<String> getServiceNameConfig() {
        return serviceName;
    }

    @Nullable
    public String getServiceNodeName() {
        String nodeName = serviceNodeName.get();
        if (nodeName == null || nodeName.trim().isEmpty()) {
            return null;
        }
        return nodeName;
    }

    public long getDelayTracerStartMs() {
        return delayTracerStart.get().getMillis();
    }

    @Nullable
    public String getServiceVersion() {
        return serviceVersion.get();
    }

    @Nullable
    public String getHostname() {
        return hostname.get();
    }

    @Nullable
    public String getEnvironment() {
        return environment.get();
    }

    public ConfigurationOption<Double> getSampleRate() {
        return sampleRate;
    }

    public int getTransactionMaxSpans() {
        return transactionMaxSpans.get();
    }

    public int getLongFieldMaxLength() {
        return longFieldMaxLength.get();
    }

    @Override
    public List<WildcardMatcher> getSanitizeFieldNames() {
        return sanitizeFieldNames.get();
    }

    @Override
    public boolean isInstrumentationEnabled(String instrumentationGroupName) {
        final Collection<String> enabledInstrumentationGroupNames = enabledInstrumentations.get();
        final Collection<String> disabledInstrumentationGroupNames = disabledInstrumentations.get();
        return (enabledInstrumentationGroupNames.isEmpty() || enabledInstrumentationGroupNames.contains(instrumentationGroupName)) &&
            !disabledInstrumentationGroupNames.contains(instrumentationGroupName) &&
            (enableExperimentalInstrumentations.get() || !instrumentationGroupName.equals("experimental"));
    }

    @Override
    public boolean isInstrumentationEnabled(Collection<String> instrumentationGroupNames) {
        return isGroupEnabled(instrumentationGroupNames) &&
            !isGroupDisabled(instrumentationGroupNames);
    }

    private boolean isGroupEnabled(Collection<String> instrumentationGroupNames) {
        final Collection<String> enabledInstrumentationGroupNames = enabledInstrumentations.get();
        if (enabledInstrumentationGroupNames.isEmpty()) {
            return true;
        }
        for (String instrumentationGroupName : instrumentationGroupNames) {
            if (enabledInstrumentationGroupNames.contains(instrumentationGroupName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGroupDisabled(Collection<String> instrumentationGroupNames) {
        Collection<String> disabledInstrumentationGroupNames = disabledInstrumentations.get();
        for (String instrumentationGroupName : instrumentationGroupNames) {
            if (disabledInstrumentationGroupNames.contains(instrumentationGroupName)) {
                return true;
            }
        }
        return !enableExperimentalInstrumentations.get() && instrumentationGroupNames.contains("experimental");
    }

    public List<WildcardMatcher> getUnnestExceptions() {
        return unnestExceptions.get();
    }

    public List<WildcardMatcher> getIgnoreExceptions() {
        return ignoreExceptions.get();
    }

    public boolean captureExceptionDetails() {
        return captureExceptionDetails.get();
    }

    @Override
    public EventType getCaptureBody() {
        return captureBody.get();
    }

    @Override
    public boolean isCaptureHeaders() {
        return captureHeaders.get();
    }

    public boolean isTypePoolCacheEnabled() {
        return typePoolCache.get();
    }

    public boolean isInstrumentAncientBytecode() {
        return instrumentAncientBytecode.get();
    }

    public boolean shouldWarmupByteBuddy() {
        return warmupByteBuddy.get();
    }

    @Nullable
    public String getBytecodeDumpPath() {
        return bytecodeDumpPath.get();
    }

    public long getThreadDumpInterval() {
        return threadDumpInterval.get().getMillis();
    }

    public boolean isTypeMatchingWithNamePreFilter() {
        return typeMatchingWithNamePreFilter.get();
    }

    public boolean isClassLoadingMatchingPreFilter() {
        return classLoadingMatchingPreFilter.get();
    }

    public List<WildcardMatcher> getClassesExcludedFromInstrumentation() {
        return classesExcludedFromInstrumentation.get();
    }

    public List<WildcardMatcher> getDefaultClassesExcludedFromInstrumentation() {
        return defaultClassesExcludedFromInstrumentation.get();
    }

    public List<WildcardMatcher> getMethodsExcludedFromInstrumentation() {
        return methodsExcludedFromInstrumentation.get();
    }

    public List<MethodMatcher> getTraceMethods() {
        return traceMethods.get();
    }

    public TimeDuration getTraceMethodsDurationThreshold() {
        return traceMethodsDurationThreshold.get();
    }

    public Map<String, String> getGlobalLabels() {
        return globalLabels.get();
    }

    boolean isCentralConfigEnabled() {
        return centralConfig.get();
    }

    public boolean isBreakdownMetricsEnabled() {
        return breakdownMetrics.get();
    }

    public boolean isElasticTraceparentHeaderEnabled() {
        return useElasticTraceparentHeader.get();
    }

    public boolean isOutgoingTraceContextHeadersInjectionDisabled() {
        return disableOutgoingTraceContextHeaders.get();
    }

    public int getTracestateSizeLimit() {
        return tracestateHeaderSizeLimit.get();
    }

    @Override
    public TimeDuration getSpanMinDuration() {
        return spanMinDuration.get();
    }

    /*
     * Makes sure to not initialize ConfigurationOption, which would initialize the logger
     */
    @Nullable
    public static String getConfigFileLocation(List<ConfigurationSource> configurationSources, boolean premain) {
        String configFileLocation = premain ? DEFAULT_CONFIG_FILE : null;
        for (ConfigurationSource source : configurationSources) {
            String value = source.getValue(CONFIG_FILE);
            if (value != null) {
                configFileLocation = value;
                break;
            }
        }

        if (configFileLocation != null) {
            String agentHome = ElasticApmAgent.getAgentHome();
            if (agentHome != null) {
                configFileLocation = configFileLocation.replace(AGENT_HOME_PLACEHOLDER, agentHome);
            }
        }

        return configFileLocation;
    }

    @Nullable
    public String getPluginsDir() {
        @Nullable
        String configFileLocation = pluginsDirLocation.get();
        if (configFileLocation != null && configFileLocation.contains(AGENT_HOME_PLACEHOLDER)) {
            String agentHome = ElasticApmAgent.getAgentHome();
            if (agentHome != null) {
                return configFileLocation.replace(AGENT_HOME_PLACEHOLDER, agentHome);
            } else {
                return null;
            }
        } else {
            return configFileLocation;
        }
    }

    public int getExternalPluginsCount() {
        String pathString = getPluginsDir();
        if (pathString == null) {
            return 0;
        }
        Path pluginsDir = Paths.get(pathString);
        if (!Files.isDirectory(pluginsDir)) {
            return 0;
        }

        int count = 0;
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(pluginsDir)) {
            for (Path p : paths) {
                if (p.getFileName().endsWith(".jar")) {
                    count++;
                }
            }
        } catch (IOException e) {
            // silently ignored
        }
        return count;
    }

    public long getMetadataDiscoveryTimeoutMs() {
        return metadataTimeoutMs.get().getMillis();
    }

    public CloudProvider getCloudProvider() {
        return cloudProvider.get();
    }

    @Override
    public boolean isEnablePublicApiAnnotationInheritance() {
        return enablePublicApiAnnotationInheritance.get();
    }

    public List<WildcardMatcher> getTransactionNameGroups() {
        return transactionNameGroups.get();
    }

    public TraceContinuationStrategy getTraceContinuationStrategy() {
        return traceContinuationStrategy.get();
    }

    @Override
    public ActivationMethod getActivationMethod() {
        return activationMethod.get();
    }

    public List<WildcardMatcher> getBaggageToAttach() {
        return baggateToAttach.get();
    }

    public boolean isContextPropagationOnly() {
        return contextPropagationOnly.get();
    }

    public boolean isRedactExceptions() {
        return (safeExceptions.get() & 1) != 0;
    }

    @Override
    public boolean isAvoidTouchingExceptions() {
        return isRedactExceptions() || !captureExceptionDetails();
    }

    @Override
    public boolean isUseServletAttributesForExceptionPropagation() {
        return (safeExceptions.get() & 2) == 0;
    }

    public enum CloudProvider {
        AUTO,
        AWS,
        GCP,
        AZURE,
        NONE
    }

    public enum TraceContinuationStrategy {
        CONTINUE,
        RESTART,
        RESTART_EXTERNAL;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    @Override
    public boolean isCaptureThreadOnStart() {
        return captureThreadOnStart.get();
    }
}
