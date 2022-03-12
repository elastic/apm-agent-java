package co.elastic.apm.agent.redisson;

import co.elastic.apm.agent.bci.PluginClassLoaderRootPackageCustomizer;

import java.util.Arrays;
import java.util.Collection;

public class RedissonPluginClassLoaderRootPackageCustomizer extends PluginClassLoaderRootPackageCustomizer {

    @Override
    public Collection<String> pluginClassLoaderRootPackages() {
        return Arrays.asList(getPluginPackage(), "co.elastic.apm.agent.redis");
    }
}
