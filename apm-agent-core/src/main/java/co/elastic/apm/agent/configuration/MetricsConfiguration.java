package co.elastic.apm.agent.configuration;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

public class MetricsConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<Boolean> dedotCustomMetrics = ConfigurationOption.booleanOption()
        .key("dedot_custom_metrics")
        .configurationCategory("Metrics")
        .description("Replaces dots with underscores in the metric names for custom metrics, such as Micrometer metrics.\n" +
            "\n" +
            "WARNING: Setting this to `false` can lead to mapping conflicts as dots indicate nesting in Elasticsearch.\n" +
            "An example of when a conflict happens is two metrics with the name `foo` and `foo.bar`.\n" +
            "The first metric maps `foo` to a number and the second metric maps `foo` as an object.")
        .dynamic(true)
        .tags("added[1.22.0]")
        .buildWithDefault(true);

    public boolean isDedotCustomMetrics() {
        return dedotCustomMetrics.get();
    }
}
