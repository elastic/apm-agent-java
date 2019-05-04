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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.bci.methodmatching.MethodMatcher;
import co.elastic.apm.agent.bci.methodmatching.configuration.MethodMatcherValueConverter;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.configuration.converter.TimeDurationValueConverter;
import co.elastic.apm.agent.configuration.validation.RegexValidator;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.ListValueConverter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static co.elastic.apm.agent.configuration.validation.RangeValidator.isInRange;

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
        .label("The name of your service")
        .description("This is used to keep all the errors and transactions of your service together\n" +
            "and is the primary filter in the Elastic APM user interface.\n" +
            "\n" +
            "The service name must conform to this regular expression: `^[a-zA-Z0-9 _-]+$`.\n" +
            "In less regexy terms:\n" +
            "Your service name must only contain characters from the ASCII alphabet, numbers, dashes, underscores and spaces.\n" +
            "\n" +
            "NOTE: When relying on auto-discovery of the service name in Servlet environments (including Spring Boot),\n" +
            "there is currently a caveat related to metrics.\n" +
            "The consequence is that the 'Metrics' tab of a service does not show process-global metrics like CPU utilization.\n" +
            "The reason is that metrics are reported with the detected default service name for the JVM,\n" +
            "for example `tomcat-application`.\n" +
            "That is because there may be multiple web applications deployed to a single JVM/servlet container.\n" +
            "However, you can view those metrics by selecting the `tomcat-application` service name, for example.\n" +
            "Future versions of the Elastic APM stack will have better support for that scenario.\n" +
            "A workaround is to explicitly set the `service_name` which means all applications deployed to the same servlet container will have the same name\n" +
            "or to disable the corresponding `*-service-name` detecting instrumentations via <<config-disable-instrumentations>>.\n" +
            "\n" +
            "NOTE: Service name auto discovery mechanisms require APM Server 7.0+.")
        .addValidator(RegexValidator.of("^[a-zA-Z0-9 _-]+$", "Your service name \"{0}\" must only contain characters " +
            "from the ASCII alphabet, numbers, dashes, underscores and spaces"))
        .buildWithDefault(ServiceNameUtil.getDefaultServiceName());

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
        .description("The name of the environment this service is deployed in, e.g. \"production\" or \"staging\".\n" +
            "\n" +
            "NOTE: The APM UI does not fully support the environment setting yet.\n" +
            "You can use the query bar to filter for a specific environment,\n" +
            "but by default the environments will be mixed together.\n" +
            "Also keep that in mind when creating alerts.")
        .build();

    private final ConfigurationOption<Double> sampleRate = ConfigurationOption.doubleOption()
        .key(SAMPLE_RATE)
        .aliasKeys("sample_rate")
        .configurationCategory(CORE_CATEGORY)
        .tags("performance")
        .description("By default, the agent will sample every transaction (e.g. request to your service). " +
            "To reduce overhead and storage requirements, you can set the sample rate to a value between 0.0 and 1.0. " +
            "We still record overall time and the result for unsampled transactions, but no context information, labels, or spans.")
        .dynamic(true)
        .addValidator(isInRange(0d, 1d))
        .buildWithDefault(1.0);

    private final ConfigurationOption<Integer> transactionMaxSpans = ConfigurationOption.integerOption()
        .key("transaction_max_spans")
        .configurationCategory(CORE_CATEGORY)
        .tags("performance")
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
            // HTTP request header for basic auth, contains passwords
            WildcardMatcher.valueOf("authorization"),
            // HTTP response header which can contain session ids
            WildcardMatcher.valueOf("set-cookie")
        ));

    private final ConfigurationOption<Collection<String>> disabledInstrumentations = ConfigurationOption.stringsOption()
        .key("disable_instrumentations")
        .aliasKeys("disabled_instrumentations")
        .configurationCategory(CORE_CATEGORY)
        .description("A list of instrumentations which should be disabled.\n" +
            "Valid options are ${allInstrumentationGroupNames}.\n" +
            "If you want to try out incubating features,\n" +
            "set the value to an empty string.")
        .buildWithDefault(Collections.<String>singleton("incubating"));

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

    private final ConfigurationOption<Boolean> typePoolCache = ConfigurationOption.booleanOption()
        .key("enable_type_pool_cache")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("When enabled, configures Byte Buddy to use a type pool cache.")
        .buildWithDefault(true);

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


    private final ConfigurationOption<List<WildcardMatcher>> classesExcludedFromInstrumentation = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("classes_excluded_from_instrumentation")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description("\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
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
        .builder(new ListValueConverter<>(MethodMatcherValueConverter.INSTANCE), List.class)
        .key("trace_methods")
        .tags("added[1.3.0,Enhancements in 1.4.0 and 1.7.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("A list of methods for with to create a transaction or span.\n" +
            "\n" +
            "The syntax is `modifier fully.qualified.class.Name#methodName(fully.qualified.parameter.Type)`.\n" +
            "You can use wildcards for the class name, the method name and the parameter types.\n" +
            "The `*` wildcard matches zero or more characters.\n" +
            "That means that a wildcard in a package name also matches sub-packages\n" +
            "Specifying the parameter types is optional.\n" +
            "The `modifier` can be omitted or one of `public`, `protected`, `private` or `*`.\n" +
            "\n" +
            "A few examples:\n" +
            "\n" +
            " - `org.example.*` added[1.4.0,Omitting the method is possible since 1.4.0]\n" +
            " - `org.example.*#*` (before 1.4.0, you need to specify a method matcher)\n" +
            " - `org.example.MyClass#myMethod`\n" +
            " - `org.example.MyClass#myMethod()`\n" +
            " - `org.example.MyClass#myMethod(java.lang.String)`\n" +
            " - `org.example.MyClass#myMe*od(java.lang.String, int)`\n" +
            " - `private org.example.MyClass#myMe*od(java.lang.String, *)`\n" +
            " - `* org.example.MyClas*#myMe*od(*.String, int[])`\n" +
            " - `public org.example.services.*Service#*`\n" +
            "\n" +
            "NOTE: Only use wildcards if necessary.\n" +
            "The more methods you match the more overhead will be caused by the agent.\n" +
            "Also note that there is a maximum amount of spans per transaction (see <<config-transaction-max-spans, `transaction_max_spans`>>).\n" +
            "\n" +
            "NOTE: The agent will create stack traces for spans which took longer than\n" +
            "<<config-span-frames-min-duration, `span_frames_min_duration`>>.\n" +
            "When tracing a large number of methods (for example by using wildcards),\n" +
            "this may lead to high overhead.\n" +
            "Consider increasing the threshold or disabling stack trace collection altogether.")
        .buildWithDefault(Collections.<MethodMatcher>emptyList());

    private final ConfigurationOption<TimeDuration> traceMethodsDurationThreshold = TimeDurationValueConverter.durationOption("ms")
        .key("trace_methods_duration_threshold")
        .tags("added[1.7.0]")
        .configurationCategory(CORE_CATEGORY)
        .description("If <<config-trace-methods, `trace_methods`>> config option is set, provides a threshold to limit spans based on \n" +
            "duration. When set to a value greater than 0, spans representing methods traced based on `trace_methods` will be discarded " +
            "by default.\n" +
            "Such methods will be traced and reported if one of the following applies:\n" +
            " - This method's duration crossed the configured threshold.\n" +
            " - This method ended with Exception.\n" +
            " - A method executed as part of the execution of this method crossed the threshold or ended with Exception.\n" +
            " - A \"forcibly-traced method\" (e.g. DB queries, HTTP exits, custom) was executed during the execution of this method.\n" +
            "Set to 0 to disable.\n" +
            "\n" +
            "NOTE: Transaction are never discarded, regardless of their duration. This configuration affects only spans.\n" +
            "In order not to break span references, all spans leading to an async operations are never discarded, regardless \n" +
            "of their duration.\n")
        .buildWithDefault(TimeDuration.of("0ms"));

    private final ConfigurationOption<Boolean> useAnnotationValueForTransactionName = ConfigurationOption.booleanOption()
        .key("use_annotation_value_for_transaction_name")
        .configurationCategory(CORE_CATEGORY)
        .tags("internal")
        .description(
            "By default, the agent will use ClassName#methodName for transaction name of JAX-RS.\n" +
                "If you need use in transaction name value from @Path annotation, you should set value to 'true'.\n")
        .dynamic(false)
        .buildWithDefault(false);

    public boolean isActive() {
        return active.get();
    }

    public boolean isInstrument() {
        return instrument.get();
    }

    public String getServiceName() {
        return serviceName.get();
    }

    public ConfigurationOption<String> getServiceNameConfig() {
        return serviceName;
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

    public Collection<String> getDisabledInstrumentations() {
        return disabledInstrumentations.get();
    }

    public List<WildcardMatcher> getUnnestExceptions() {
        return unnestExceptions.get();
    }

    public boolean isTypePoolCacheEnabled() {
        return typePoolCache.get();
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

    public List<WildcardMatcher> getMethodsExcludedFromInstrumentation() {
        return methodsExcludedFromInstrumentation.get();
    }

    public List<MethodMatcher> getTraceMethods() {
        return traceMethods.get();
    }

    public TimeDuration getTraceMethodsDurationThreshold() {
        return traceMethodsDurationThreshold.get();
    }

    public boolean isUseAnnotationValueForTransactionName() { return useAnnotationValueForTransactionName.get(); }
}
