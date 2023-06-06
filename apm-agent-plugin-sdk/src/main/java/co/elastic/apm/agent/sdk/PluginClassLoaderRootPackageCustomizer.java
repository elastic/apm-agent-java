/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.sdk;

import java.lang.instrument.Instrumentation;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
        pluginPackage = getPluginPackageFromClassName(className);
    }

    public static String getPluginPackageFromClassName(String className) {
        if (!className.startsWith(EMBEDDED_PLUGINS_PACKAGE_PREFIX)) {
            throw new IllegalArgumentException("invalid instrumentation class location : " + className);
        }
        return className.substring(0, className.indexOf('.', EMBEDDED_PLUGINS_PACKAGE_PREFIX.length()));
    }

    public final String getPluginPackage() {
        return pluginPackage;
    }

    /**
     * All classes in the provided packages except for the ones annotated with {@link co.elastic.apm.agent.sdk.state.GlobalState}
     * and classes extending {@code org.stagemonitor.configuration.ConfigurationOptionProvider}
     * will be loaded from a dedicated plugin class loader that has access to both the instrumented classes and the agent classes.
     * If the {@linkplain #getPluginPackage() plugin package} should be part of the root packages, implementations need to explicitly add it.
     */
    public abstract Collection<String> pluginClassLoaderRootPackages();

    /**
     * Starting with Java 9, Java added a module system which allows restricting access to code within modules.
     * From Java 9 to 16, illegal access print a warning on a console.
     * Starting with Java 17, Illegal access cause {@link  IllegalAccessException}s.
     * <p>
     * Instrumentation plugins are loaded in an isolated classloader and therefore in an unnamed module.
     * This module by default cannot access anything "private" within other modules, including
     * the module containing the instrumented class. If such access is required for the plugin,
     * access can be granted using this method. Before anything from the plugin is invoked,
     * {@link Instrumentation#redefineModule(Module, Set, Map, Map, Set, Map) will be used to give the classloader
     * full access ("open") to the target module.}
     * <p>
     * <p>
     * Each entry within the result map corresponds to a module which needs to be made accessible.
     * Each module is looked up based on a "witness" class which resides within the respective module.
     * Therefore, the keys of the return value from this map are the fully qualified names of these witness classes.
     * The values of the map is a set of package names to open for the instrumentation plugin (like the
     * packages passed to the --add-opens command line argument).
     * <p>
     * IMPORTANT: The "witness" classes are looked up when an advice from the plugin is invoked for the first time.
     * It is best to provide classes which are known to be already loaded at that point in time in order
     * to not mess with class initialization order. For example, the instrumented class which invoked the
     * advice would be a safe choice.
     *
     * @return the map of "witness" FQNs to a collection of package names to open
     */
    public Map<String, ? extends Collection<String>> requiredModuleOpens() {
        return Collections.emptyMap();
    }
}
