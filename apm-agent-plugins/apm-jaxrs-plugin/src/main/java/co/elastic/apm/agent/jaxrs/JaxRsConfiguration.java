package co.elastic.apm.agent.jaxrs;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

/**
 * Configuration provider for the apm jax-rs plugin
 */
public class JaxRsConfiguration extends ConfigurationOptionProvider {
    private static final String JAXRS_CATEGORY = "jaxrs";

    private final ConfigurationOption<Boolean> allowPathOnHierarchy = ConfigurationOption.booleanOption()
        .key("allow_path_on_hierarchy")
        .configurationCategory(JAXRS_CATEGORY)
        .tags("performance")
        .description("If set to `true`,\n" +
            "the agent will find jax-rs webservices where only the a superclass or interface is annotated with @Path.\n" +
            "This is not supported according to the jax-ws spec, but some implementations support it.\n" +
            "\n" +
            "NOTE: Setting this to `true` will reduce startup performance because of class hierarchy scanning. Only use when needed")
        .dynamic(false)
        .buildWithDefault(false);


    /**
     * @return if true, the jax-rs plugin must scan for @Path annotations in the class hierarchy of classes.
     * if false, only @Path annotations on implementation classes are considered.
     */
    public boolean isAllowPathOnHierarchy() {
        return allowPathOnHierarchy.get();
    }
}
