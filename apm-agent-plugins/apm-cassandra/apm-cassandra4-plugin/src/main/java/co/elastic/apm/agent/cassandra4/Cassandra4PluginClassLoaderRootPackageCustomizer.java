package co.elastic.apm.agent.cassandra4;

import co.elastic.apm.agent.bci.PluginClassLoaderRootPackageCustomizer;

import java.util.Arrays;
import java.util.Collection;

public class Cassandra4PluginClassLoaderRootPackageCustomizer extends PluginClassLoaderRootPackageCustomizer {
    @Override
    public Collection<String> pluginClassLoaderRootPackages() {
        return Arrays.asList(getPluginPackage(),"co.elastic.apm.agent.cassandra");
    }
}
