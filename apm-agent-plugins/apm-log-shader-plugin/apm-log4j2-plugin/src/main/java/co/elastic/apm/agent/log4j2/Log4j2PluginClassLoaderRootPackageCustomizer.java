package co.elastic.apm.agent.log4j2;

import co.elastic.apm.agent.bci.PluginClassLoaderRootPackageCustomizer;

import java.util.Arrays;
import java.util.Collection;

public class Log4j2PluginClassLoaderRootPackageCustomizer extends PluginClassLoaderRootPackageCustomizer {
    @Override
    public Collection<String> pluginClassLoaderRootPackages() {
        return Arrays.asList(getPluginPackage(), "co.elastic.logging");
    }
}
