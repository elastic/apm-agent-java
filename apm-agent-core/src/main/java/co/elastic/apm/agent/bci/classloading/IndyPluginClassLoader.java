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
package co.elastic.apm.agent.bci.classloading;

import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.net.URL;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * The plugin class loader has both the agent class loader and the target class loader as the parent.
 * This is important so that the plugin class loader has direct access to the agent class loader
 * otherwise, filtering class loaders (like OSGi) have a chance to interfere
 *
 * @see co.elastic.apm.agent.bci.IndyBootstrap
 */
public class IndyPluginClassLoader extends ByteArrayClassLoader.ChildFirst {

    private static final ClassLoader SYSTEM_CLASS_LOADER = ClassLoader.getSystemClassLoader();

    public IndyPluginClassLoader(@Nullable ClassLoader targetClassLoader, ClassLoader agentClassLoader, Map<String, byte[]> typeDefinitions) {
        // See getResource on why we're using PersistenceHandler.LATENT over PersistenceHandler.MANIFEST
        super(getParent(targetClassLoader, agentClassLoader), true, typeDefinitions, PersistenceHandler.LATENT);
    }

    private static ClassLoader getParent(@Nullable ClassLoader targetClassLoader, ClassLoader agentClassLoader) {
        if (targetClassLoader == null) {
            // the MultipleParentClassLoader doesn't support null values
            // the agent class loader already has the bootstrap class loader as the parent
            return agentClassLoader;
        }
        if (agentClassLoader == SYSTEM_CLASS_LOADER) {
            // we're inside a unit test
            // in unit tests, we need to search the target class loader first, unless it's an agent class
            // that's because the system class loader may contain another version of the library under test
            // see also co.elastic.apm.agent.TestClassWithDependencyRunner
            return new DiscriminatingMultiParentClassLoader(
                agentClassLoader, startsWith("co.elastic.apm.agent").or(startsWith("net.bytebuddy")),
                targetClassLoader, ElementMatchers.<String>any());
        } else {
            // In prod, always search in the agent class loader first.
            // This ensures that we're referencing the agent bundled classes in advices rather than the ones form the application
            // (for example Byte Buddy, or even dependencies that are bundled in external plugins etc.)
            // However, we need to avoid looking up classes from the agent class loader that we want to instrument.
            // For example, we're instrumenting log4j2 to support ecs_log_reformatting which is also available within the agent class loader.
            // Within the context of an instrumentation plugin, referencing log4j2 should always reference the instrumented types, not the ones shipped with the agent.
            // The list of packages not to load should correspond with matching dependency exclusions from the apm-agent-core in apm-agent-plugins/pom.xml
            // As we're using a custom logging facade, plugins don't need to refer to the agent-bundled log4j2 or slf4j.
            return new DiscriminatingMultiParentClassLoader(
                agentClassLoader, not(startsWith("org.apache.logging.log4j").and(not(startsWith("org.slf4j")))),
                targetClassLoader, ElementMatchers.<String>any());
        }
    }

    /**
     * This class loader uses {@link PersistenceHandler#LATENT} (see {@link #IndyPluginClassLoader})
     * as it reduces the memory footprint of the class loader compared to {@link PersistenceHandler#MANIFEST}.
     * With {@link PersistenceHandler#MANIFEST}, after a class has been loaded, the class file byte[] is kept in the typeDefinitions map
     * so that the class can be looked up as a resource.
     * With {@link PersistenceHandler#LATENT}, the class file byte[] is removed from the typeDefinitions after the corresponding class has been loaded.
     * This implies that the class can't be looked up as a resource.
     * The method from the super class even disallows delegation to the parent (as it's a child-first class loader).
     * Overriding this method ensures that we can look up the class resource from the parent class loader (agent class loader).
     */
    @Override
    public URL getResource(String name) {
        URL url = super.getResource(name);
        return url != null
            ? url
            : getParent().getResource(name);
    }

    public static StartsWithElementMatcher startsWith(String prefix) {
        return new StartsWithElementMatcher(prefix);
    }

    private static class StartsWithElementMatcher extends ElementMatcher.Junction.AbstractBase<String> {

        private final String prefix;

        private StartsWithElementMatcher(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public boolean matches(String s) {
            return s.startsWith(prefix);
        }
    }
}
