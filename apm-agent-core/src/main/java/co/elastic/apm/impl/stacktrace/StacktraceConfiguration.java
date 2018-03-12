package co.elastic.apm.impl.stacktrace;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Collection;
import java.util.Collections;

public class StacktraceConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<Collection<String>> applicationPackages = ConfigurationOption.stringsOption()
        .key("application_packages")
        .description("Used to determine whether a stack trace frame is an 'in-app frame' or a 'library frame'.")
        .dynamic(true)
        .buildWithDefault(Collections.<String>emptyList());

    private final ConfigurationOption<Integer> stackTraceLimit = ConfigurationOption.integerOption()
        .key("stack_trace_limit")
        .description("Setting it to 0 will disable stack trace collection. " +
            "Any positive integer value will be used as the maximum number of frames to collect. " +
            "Setting it -1 means that all frames will be collected.")
        .dynamic(true)
        .buildWithDefault(50);

    private final ConfigurationOption<Integer> spanFramesMinDurationMs = ConfigurationOption.integerOption()
        .key("span_frames_min_duration_ms")
        .description("In its default settings, the APM agent will collect a stack trace with every recorded span.\n" +
            "While this is very helpful to find the exact place in your code that causes the span, " +
            "collecting this stack trace does have some overhead. " +
            "\n" +
            "With the default setting, `-1`, stack traces will be collected for all spans. " +
            "Setting it to a positive value, e.g. `5`, will limit stack trace collection to spans " +
            "with durations equal or longer than the given value in milliseconds, e.g. 5 milliseconds.\n" +
            "\n" +
            "To disable stack trace collection for spans completely, set the value to 0.")
        .dynamic(true)
        .buildWithDefault(-1);

    public Collection<String> getApplicationPackages() {
        return applicationPackages.get();
    }

    public int getStackTraceLimit() {
        return stackTraceLimit.get();
    }

    public int getSpanFramesMinDurationMs() {
        return spanFramesMinDurationMs.getValue();
    }
}
