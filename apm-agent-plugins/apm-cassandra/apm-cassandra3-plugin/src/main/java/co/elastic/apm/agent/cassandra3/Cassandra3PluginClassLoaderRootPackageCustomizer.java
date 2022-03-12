package co.elastic.apm.agent.cassandra3;

import co.elastic.apm.agent.bci.PluginClassLoaderRootPackageCustomizer;

import java.util.Arrays;
import java.util.Collection;

public class Cassandra3PluginClassLoaderRootPackageCustomizer extends PluginClassLoaderRootPackageCustomizer {
    @Override
    public Collection<String> pluginClassLoaderRootPackages() {
        return Arrays.asList(getPluginPackage(),"co.elastic.apm.agent.cassandra");
    }
}
