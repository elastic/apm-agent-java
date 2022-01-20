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
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.bci.classloading.IndyPluginClassLoader;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class IndyPluginClassLoaderFactory {

    private static final Logger logger = LoggerFactory.getLogger(IndyPluginClassLoaderFactory.class);

    private static final Map<ClassLoader, Map<Collection<String>, WeakReference<ClassLoader>>> alreadyInjected = new WeakHashMap<ClassLoader, Map<Collection<String>, WeakReference<ClassLoader>>>();

    /**
     * Creates an isolated CL that has two parents: the target class loader and the agent CL.
     * The agent class loader is an isolated CL that is a child of the bootstrap CL.
     */
    public synchronized static ClassLoader getOrCreatePluginClassLoader(@Nullable ClassLoader targetClassLoader,
                                                                        List<String> classesToInject,
                                                                        ClassLoader agentClassLoader,
                                                                        ClassFileLocator classFileLocator,
                                                                        ElementMatcher<? super TypeDescription> exclusionMatcher) throws Exception {
        classesToInject = new ArrayList<>(classesToInject);

        Map<Collection<String>, WeakReference<ClassLoader>> injectedClasses = getOrCreateInjectedClasses(targetClassLoader);
        if (injectedClasses.containsKey(classesToInject)) {
            ClassLoader pluginClassLoader = injectedClasses.get(classesToInject).get();
            if (pluginClassLoader == null) {
                injectedClasses.remove(classesToInject);
            } else {
                return pluginClassLoader;
            }
        }

        List<String> classesToInjectCopy = new ArrayList<>(classesToInject.size());
        TypePool pool = new TypePool.Default.WithLazyResolution(TypePool.CacheProvider.NoOp.INSTANCE, classFileLocator, TypePool.Default.ReaderMode.FAST);
        for (Iterator<String> iterator = classesToInject.iterator(); iterator.hasNext(); ) {
            String className = iterator.next();
            boolean excluded;
            try {
                excluded = exclusionMatcher.matches(pool.describe(className).resolve());
            } catch (Exception e) {
                // in case a matcher fails, for example because it can't resolve a type description
                excluded = false;
            }
            if (!excluded) {
                classesToInjectCopy.add(className);
            }
        }
        logger.debug("Creating plugin class loader for {} containing {}", targetClassLoader, classesToInjectCopy);

        Map<String, byte[]> typeDefinitions = getTypeDefinitions(classesToInjectCopy, classFileLocator);
        // child first semantics are important here as the plugin CL contains classes that are also present in the agent CL
        ClassLoader pluginClassLoader = new IndyPluginClassLoader(targetClassLoader, agentClassLoader, typeDefinitions);
        injectedClasses.put(classesToInject, new WeakReference<>(pluginClassLoader));

        return pluginClassLoader;
    }

    private static Map<Collection<String>, WeakReference<ClassLoader>> getOrCreateInjectedClasses(@Nullable ClassLoader targetClassLoader) {
        Map<Collection<String>, WeakReference<ClassLoader>> injectedClasses = alreadyInjected.get(targetClassLoader);
        if (injectedClasses == null) {
            injectedClasses = new HashMap<>();
            alreadyInjected.put(targetClassLoader, injectedClasses);
        }
        return injectedClasses;
    }

    public synchronized static void clear() {
        alreadyInjected.clear();
    }

    private static Map<String, byte[]> getTypeDefinitions(List<String> helperClassNames, ClassFileLocator classFileLocator) throws IOException {
        Map<String, byte[]> typeDefinitions = new HashMap<>();
        for (final String helperName : helperClassNames) {
            final byte[] classBytes = classFileLocator.locate(helperName).resolve();
            typeDefinitions.put(helperName, classBytes);
        }
        return typeDefinitions;
    }

}
