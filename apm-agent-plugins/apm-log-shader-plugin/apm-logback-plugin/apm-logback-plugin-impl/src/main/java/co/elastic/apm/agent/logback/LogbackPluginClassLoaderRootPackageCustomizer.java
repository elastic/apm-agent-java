package co.elastic.apm.agent.logback;

import co.elastic.apm.agent.bci.PluginClassLoaderRootPackageCustomizer;

import java.util.Arrays;
import java.util.Collection;

public class LogbackPluginClassLoaderRootPackageCustomizer extends PluginClassLoaderRootPackageCustomizer {
    @Override
    public Collection<String> pluginClassLoaderRootPackages() {
        return Arrays.asList(getPluginPackage(), "co.elastic.logging");
    }
}
