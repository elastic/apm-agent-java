package co.elastic.apm.agent.jaxrs.configuration;

import co.elastic.apm.agent.jaxrs.JaxRsConfiguration;
import co.elastic.apm.plugin.spi.MinimalConfiguration;

public class MinimalJaxRsConfiguration implements JaxRsConfiguration, MinimalConfiguration {

    private static final MinimalJaxRsConfiguration INSTANCE = new MinimalJaxRsConfiguration();

    public static MinimalJaxRsConfiguration provider() {
        return INSTANCE;
    }

    private MinimalJaxRsConfiguration() {
    }
    @Override
    public boolean isEnableJaxrsAnnotationInheritance() {
        return true;
    }

    @Override
    public boolean isUseJaxRsPathForTransactionName() {
        return false;
    }
}
