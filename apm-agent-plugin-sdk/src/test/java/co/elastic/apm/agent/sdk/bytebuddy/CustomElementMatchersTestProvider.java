package co.elastic.apm.agent.sdk.bytebuddy;

import javax.annotation.Nullable;

public class CustomElementMatchersTestProvider implements CustomElementMatchers.CustomElementMatchersProvider {

    @Override
    public boolean isAgentClassLoader(@Nullable ClassLoader classLoader) {
        return classLoader instanceof SampleAgentClassLoader;
    }

    @Override
    public boolean isInternalPluginClassLoader(@Nullable ClassLoader classLoader) {
        return classLoader instanceof SampleInternalPluginClassLoader;
    }

    static class SampleAgentClassLoader extends ClassLoader {
    }

    static class SampleInternalPluginClassLoader extends ClassLoader {
    }
}
