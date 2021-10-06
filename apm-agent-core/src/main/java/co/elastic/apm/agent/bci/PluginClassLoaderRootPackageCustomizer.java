package co.elastic.apm.agent.bci;

import java.util.Collection;

/**
 * This class must be provided at most once per {@linkplain #getPluginPackage() plugin package}.
 */
public abstract class PluginClassLoaderRootPackageCustomizer {

    /**
     * The root package name prefix that all embedded plugins classes must start with
     */
    private static final String EMBEDDED_PLUGINS_PACKAGE_PREFIX = "co.elastic.apm.agent.";

    private final String pluginPackage;

    public PluginClassLoaderRootPackageCustomizer() {
        String className = getClass().getName();
        if (!className.startsWith(EMBEDDED_PLUGINS_PACKAGE_PREFIX)) {
            throw new IllegalArgumentException("invalid instrumentation class location : " + className);
        }
        this.pluginPackage = className.substring(0, className.indexOf('.', EMBEDDED_PLUGINS_PACKAGE_PREFIX.length()));
    }

    public final String getPluginPackage() {
        return pluginPackage;
    }

    /**
     * All classes in the provided packages except for the ones annotated with {@link co.elastic.apm.agent.sdk.state.GlobalState}
     * and classes extending {@link org.stagemonitor.configuration.ConfigurationOptionProvider}
     * will be loaded from a dedicated plugin class loader that has access to both the instrumented classes and the agent classes.
     * If the {@linkplain #getPluginPackage() plugin package} should be part of the root packages, implementations need to explicitly add it.
     */
    public abstract Collection<String> pluginClassLoaderRootPackages();
}
