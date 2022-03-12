package co.elastic.apm.agent.asynchttpclient;

import co.elastic.apm.agent.bci.PluginClassLoaderRootPackageCustomizer;

import java.util.Arrays;
import java.util.Collection;

public class AsyncHttpClientPluginClassLoaderRootPackageCustomizer extends PluginClassLoaderRootPackageCustomizer {

    @Override
    public Collection<String> pluginClassLoaderRootPackages() {
        return Arrays.asList(getPluginPackage(), "co.elastic.apm.agent.httpclient");
    }
}
