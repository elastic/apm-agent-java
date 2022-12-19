package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Collection;
import java.util.Collections;

public class JmsConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<Collection<String>> jmsListenerPackages = ConfigurationOption
        .stringsOption()
        .key("jms_listener_packages")
        .configurationCategory("JMS")
        .tags("internal")
        .description("Defines which packages contain JMS MessageListener implementations for instrumentation." +
            "\n" +
            "When set to a non-empty value, only the classes matching configuration will be instrumented.\n" +
            "This configuration option helps to make MessageListener type matching faster and improve application startup performance.\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION
        )
        .dynamic(false)
        .buildWithDefault(Collections.emptyList());

    public Collection<String> getJmsListenerPackages() {
        return jmsListenerPackages.get();
    }
}
