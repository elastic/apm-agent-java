package co.elastic.apm.configuration;

import co.elastic.apm.configuration.validation.RegexValidator;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

public class CoreConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<Boolean> active = ConfigurationOption.booleanOption()
        .key("active")
        .description("A boolean specifying if the agent should be active or not. " +
            "If active, the agent will instrument incoming HTTP requests and track errors.\n" +
            "\n" +
            "You can use this setting to dynamically disable Elastic APM at runtime.")
        .dynamic(true)
        .buildWithDefault(true);

    private final ConfigurationOption<Boolean> instrument = ConfigurationOption.booleanOption()
        .key("instrument")
        .description("A boolean specifying if the agent should instrument the application to collect performance metrics for the app. " +
            "When set to false, Elastic APM will not affect your application at all.\n" +
            "\n" +
            "Note that both active and instrument needs to be true for instrumentation to be running.")
        .buildWithDefault(true);

    private final ConfigurationOption<String> serviceName = ConfigurationOption.<String>stringOption()
        .key("service_name")
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
        .key("service_version")
        .description("A version string for the currently deployed version of the service. If you don’t version your deployments, " +
            "the recommended value for this field is the commit identifier of the deployed revision, " +
            "e.g. the output of git rev-parse HEAD.")
        .build();

    private final ConfigurationOption<String> environment = ConfigurationOption.<String>stringOption()
        .key("environment")
        .description("The name of the environment this service is deployed in, e.g. \"production\" or \"staging\".")
        .build();

    private final ConfigurationOption<Double> sampleRate = ConfigurationOption.<Double>doubleOption()
        .key("sample_rate")
        .description("By default, the agent will sample every transaction (e.g. request to your service). " +
            "To reduce overhead and storage requirements, you can set the sample rate to a value between 0.0 and 1.0. " +
            "We still record overall time and the result for unsampled transactions, but no context information, tags, or spans.")
        .dynamic(true)
        .buildWithDefault(1.0);

    private final ConfigurationOption<Integer> transactionMaxSpans = ConfigurationOption.integerOption()
        .key("transaction_max_spans")
        .description("Limits the amount of spans that are recorded per transaction.\n\n" +
            "This is helpful in cases where a transaction creates a very high amount of spans (e.g. thousands of SQL queries).\n\n" +
            "Setting an upper limit will prevent overloading the agent and the APM server with too much work for such edge cases.")
        .dynamic(true)
        .buildWithDefault(500);

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

    public int getTransactionMaxSpans() {
        return transactionMaxSpans.get();
    }
}
