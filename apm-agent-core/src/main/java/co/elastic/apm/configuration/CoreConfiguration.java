package co.elastic.apm.configuration;

import co.elastic.apm.configuration.validation.RegexValidator;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Collection;
import java.util.Collections;

public class CoreConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<Boolean> active = ConfigurationOption.booleanOption()
        .key("elastic.apm.active")
        .description("A boolean specifying if the agent should be active or not. " +
            "If active, the agent will instrument incoming HTTP requests and track errors.\n" +
            "\n" +
            "You can use this setting to dynamically disable Elastic APM at runtime.")
        .dynamic(true)
        .buildWithDefault(true);

    private final ConfigurationOption<Boolean> instrument = ConfigurationOption.booleanOption()
        .key("elastic.apm.instrument")
        .description("A boolean specifying if the agent should instrument the application to collect performance metrics for the app. " +
            "When set to false, Elastic APM will not affect your application at all.\n" +
            "\n" +
            "Note that both active and instrument needs to be true for instrumentation to be running.")
        .buildWithDefault(true);

    private final ConfigurationOption<String> serviceName = ConfigurationOption.<String>stringOption()
        .key("elastic.apm.service_name")
        .label("The name of your service")
        .description("This is used to keep all the errors and transactions of your service together and is the primary filter in the" +
            "Elastic APM user interface.\n" +
            "\n" +
            "NOTE: The service name must conform to this regular expression: ^[a-zA-Z0-9 _-]+$. In less regexy terms: Your service name " +
            "must only contain characters from the ASCII alphabet, numbers, dashes, underscores and spaces.")
        .addValidator(RegexValidator.of("^[a-zA-Z0-9 _-]+$", "Your service name \"{0}\" must only contain characters " +
            "from the ASCII alphabet, numbers, dashes, underscores and spaces"))
        .buildRequired();

    private final ConfigurationOption<String> serviceVersion = ConfigurationOption.<String>stringOption()
        .key("elastic.apm.service_version")
        .description("A version string for the currently deployed version of the service. If you don’t version your deployments, " +
            "the recommended value for this field is the commit identifier of the deployed revision, " +
            "e.g. the output of git rev-parse HEAD.")
        .build();

    private final ConfigurationOption<String> environment = ConfigurationOption.<String>stringOption()
        .key("elastic.apm.environment")
        .description("The name of the environment this service is deployed in, e.g. \"production\" or \"staging\".")
        .build();

    private final ConfigurationOption<Double> sampleRate = ConfigurationOption.<Double>doubleOption()
        .key("elastic.apm.sample_rate")
        .description("By default, the agent will sample every transaction (e.g. request to your service). " +
            "To reduce overhead and storage requirements, you can set the sample rate to a value between 0.0 and 1.0. " +
            "We still record overall time and the result for unsampled transactions, but no context information, tags, or spans.")
        .dynamic(true)
        .buildWithDefault(1.0);

    private final ConfigurationOption<Collection<String>> applicationPackages = ConfigurationOption.<Collection<String>>stringsOption()
        .key("elastic.apm.application_packages")
        .description("Used to determine whether a stack trace frame is an 'in-app frame' or a 'library frame'.")
        .dynamic(true)
        .buildWithDefault(Collections.<String>emptyList());

    private final ConfigurationOption<Integer> stackTraceLimit = ConfigurationOption.<Integer>integerOption()
        .key("elastic.apm.stack_trace_limit")
        .description("Setting it to 0 will disable stack trace collection. " +
            "Any positive integer value will be used as the maximum number of frames to collect. " +
            "Setting it -1 means that all frames will be collected.")
        .dynamic(true)
        .buildWithDefault(50);

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

    public double getSampleRate() {
        return sampleRate.get();
    }

    public Collection<String> getApplicationPackages() {
        return applicationPackages.get();
    }

    public int getStackTraceLimit() {
        return stackTraceLimit.get();
    }
}
