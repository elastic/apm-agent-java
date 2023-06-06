package co.elastic.apm.agent.bytebuddy;

import co.elastic.apm.agent.bci.classloading.IndyPluginClassLoader;
import co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers;

import javax.annotation.Nullable;

public class CustomElementMatchersProviderImpl implements CustomElementMatchers.CustomElementMatchersProvider {

    @Override
    public boolean isAgentClassLoader(@Nullable ClassLoader classLoader) {
        return classLoader != null && classLoader.getClass().getName().startsWith("co.elastic.apm.");
    }

    @Override
    public boolean isInternalPluginClassLoader(@Nullable ClassLoader classLoader) {
        return classLoader instanceof IndyPluginClassLoader;
    }
}
