package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.bci.PluginClassLoaderRootPackageCustomizer;

import java.util.Arrays;
import java.util.Collection;

public class OkHttp3PluginClassLoaderRootPackageCustomizer extends PluginClassLoaderRootPackageCustomizer {

    @Override
    public Collection<String> pluginClassLoaderRootPackages() {
        return Arrays.asList(getPluginPackage(), "co.elastic.apm.agent.httpclient");
    }
}
