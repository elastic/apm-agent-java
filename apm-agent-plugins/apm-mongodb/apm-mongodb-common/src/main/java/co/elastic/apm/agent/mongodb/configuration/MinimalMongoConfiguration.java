package co.elastic.apm.agent.mongodb.configuration;

import co.elastic.apm.agent.mongodb.MongoConfiguration;
import co.elastic.apm.plugin.spi.MinimalConfiguration;
import co.elastic.apm.plugin.spi.WildcardMatcher;

import java.util.Collections;
import java.util.List;

public class MinimalMongoConfiguration implements MongoConfiguration, MinimalConfiguration {

    private static final MinimalMongoConfiguration INSTANCE = new MinimalMongoConfiguration();

    public static MinimalMongoConfiguration provider() {
        return INSTANCE;
    }

    private MinimalMongoConfiguration() {
    }

    @Override
    public List<WildcardMatcher> getCaptureStatementCommands() {
        return Collections.<WildcardMatcher>emptyList();
    }
}
