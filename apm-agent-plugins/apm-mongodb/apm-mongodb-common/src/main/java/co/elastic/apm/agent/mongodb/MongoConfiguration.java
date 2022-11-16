package co.elastic.apm.agent.mongodb;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.ListValueConverter;

import java.util.Arrays;
import java.util.List;

public class MongoConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<List<WildcardMatcher>> captureStatementCommands = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("mongodb_capture_statement_commands")
        .configurationCategory("mongodb")
        .description(""+
            WildcardMatcher.DOCUMENTATION
        )
        .dynamic(true)
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.valueOf("find"),
            WildcardMatcher.valueOf("aggregate"),
            WildcardMatcher.valueOf("count"),
            WildcardMatcher.valueOf("distinct"),
            WildcardMatcher.valueOf("mapReduce")
        ));

    public List<WildcardMatcher> getCaptureStatementCommands() {
        return captureStatementCommands.get();
    }

}
