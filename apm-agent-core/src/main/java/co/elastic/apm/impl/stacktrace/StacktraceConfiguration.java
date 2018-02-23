package co.elastic.apm.impl.stacktrace;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Collection;
import java.util.Collections;

public class StacktraceConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<Collection<String>> applicationPackages = ConfigurationOption.<Collection<String>>stringsOption()
        .key("application_packages")
        .description("Used to determine whether a stack trace frame is an 'in-app frame' or a 'library frame'.")
        .dynamic(true)
        .buildWithDefault(Collections.<String>emptyList());

    private final ConfigurationOption<Integer> stackTraceLimit = ConfigurationOption.<Integer>integerOption()
        .key("stack_trace_limit")
        .description("Setting it to 0 will disable stack trace collection. " +
            "Any positive integer value will be used as the maximum number of frames to collect. " +
            "Setting it -1 means that all frames will be collected.")
        .dynamic(true)
        .buildWithDefault(50);

    public Collection<String> getApplicationPackages() {
        return applicationPackages.get();
    }

    public int getStackTraceLimit() {
        return stackTraceLimit.get();
    }
}
