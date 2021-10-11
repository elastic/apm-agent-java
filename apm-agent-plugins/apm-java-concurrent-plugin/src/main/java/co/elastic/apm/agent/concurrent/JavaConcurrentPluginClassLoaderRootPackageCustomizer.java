package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.bci.PluginClassLoaderRootPackageCustomizer;

import java.util.Collection;
import java.util.Collections;

public class JavaConcurrentPluginClassLoaderRootPackageCustomizer extends PluginClassLoaderRootPackageCustomizer {
    @Override
    public Collection<String> pluginClassLoaderRootPackages() {
        // the classes of this plugin don't need to be injected into a no plugin CL
        // as all types referenced in this plugin are available form the bootstrap CL, thus also the agent CL
        return Collections.emptyList();
    }
}
