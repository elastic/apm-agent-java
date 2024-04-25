package co.elastic.apm.agent.configuration;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

public class UniversalProfilingConfiguration extends ConfigurationOptionProvider {

    private static final String PROFILING_CATEGORY = "Profiling";

    private final ConfigurationOption<Boolean> enabled = ConfigurationOption.booleanOption()
        .key("universal_profiling_integration_enabled")
        .tags("added[1.50.0]", "internal")
        .configurationCategory(PROFILING_CATEGORY)
        .description("If enabled, the apm agent will correlate it's transaction with the profiling data from elastic universal profiling running on the same host.")
        .buildWithDefault(false);

    private final ConfigurationOption<Long> bufferSize = ConfigurationOption.longOption()
        .key("universal_profiling_integration_buffer_size")
        .tags("added[1.50.0]", "internal")
        .configurationCategory(PROFILING_CATEGORY)
        .description("The feature needs to buffer ended local-root spans for a short duration to ensure that all of its profiling data has been received." +
                     "This configuration option configures the buffer size in number of spans. " +
                     "The higher the number of local root spans per second, the higher this buffer size should be set.\n" +
                     "The agent will log a warning if it is not capable of buffering a span due to insufficient buffer size. " +
                     "This will cause the span to be exported immediately instead with possibly incomplete profiling correlation data.")
        .buildWithDefault(4096L);

    private final ConfigurationOption<String> socketDir = ConfigurationOption.stringOption()
        .key("universal_profiling_integration_socket_dir")
        .tags("added[1.50.0]", "internal")
        .configurationCategory(PROFILING_CATEGORY)
        .description("The extension needs to bind a socket to a file for communicating with the universal profiling host agent." +
                     "This configuration option can be used to change the location. " +
                     "Note that the total path name (including the socket) must not exceed 100 characters due to OS restrictions.\n" +
                     "If unset, the value of the `java.io.tmpdir` system property will be used.")
        .build();

    public boolean isEnabled() {
        return enabled.get();
    }

    public long getBufferSize() {
        return bufferSize.get();
    }

    public String getSocketDir() {
        String dir = socketDir.get();
        return dir == null || dir.isEmpty() ? System.getProperty("java.io.tmpdir") : dir;
    }

}
