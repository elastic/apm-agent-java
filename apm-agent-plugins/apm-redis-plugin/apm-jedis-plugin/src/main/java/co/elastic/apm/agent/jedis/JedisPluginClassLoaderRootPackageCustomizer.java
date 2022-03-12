package co.elastic.apm.agent.jedis;

import co.elastic.apm.agent.bci.PluginClassLoaderRootPackageCustomizer;

import java.util.Arrays;
import java.util.Collection;

public class JedisPluginClassLoaderRootPackageCustomizer extends PluginClassLoaderRootPackageCustomizer {

    @Override
    public Collection<String> pluginClassLoaderRootPackages() {
        return Arrays.asList(getPluginPackage(),"co.elastic.apm.agent.redis");
    }
}
