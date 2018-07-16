/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.bci;

import co.elastic.apm.impl.ElasticApmTracer;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// TODO docs (see co/elastic/apm/servlet/helper/package-info.java)
public class HelperClassManager {

    public static final String BOOTSTRAP_CLASSLOADER = "_BOOTSTRAP_CLASSLOADER_";
    private final ElasticApmTracer tracer;
    private final ConcurrentMap<Class<?>, List<String>> helperClassDefinitions = new ConcurrentHashMap<>();
    // TODO use weak maps to prevent class loader leaks
    private final ConcurrentMap<Object, Map<Class<?>, Object>> helperClassCache = new ConcurrentHashMap<>();

    HelperClassManager(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    public void registerHelperClasses(Class<?> helperInterface, String implementation, String... additionalHelpers) {
        final ArrayList<String> helperClassNames = new ArrayList<>(additionalHelpers.length + 1);
        helperClassNames.add(implementation);
        helperClassNames.addAll(Arrays.asList(additionalHelpers));
        helperClassDefinitions.putIfAbsent(helperInterface, helperClassNames);
    }

    public <T> T getHelperClass(@Nullable ClassLoader targetClassLoader, Class<? super T> helperInterface) {
        if (!helperClassCache.containsKey(maskNull(targetClassLoader))) {
            helperClassCache.putIfAbsent(maskNull(targetClassLoader), new ConcurrentHashMap<Class<?>, Object>());
        }
        final Map<Class<?>, Object> helpersForCL = helperClassCache.get(maskNull(targetClassLoader));
        if (!helpersForCL.containsKey(helperInterface)) {
            synchronized (helperInterface) {
                final T helper = createHelper(targetClassLoader, helperClassDefinitions.get(helperInterface));
                helpersForCL.put(helperInterface, helper);
            }
        }
        return (T) helpersForCL.get(helperInterface);
    }

    @Nonnull
    private Object maskNull(@Nullable ClassLoader targetClassLoader) {
        return targetClassLoader != null ? targetClassLoader : BOOTSTRAP_CLASSLOADER;
    }

    private <T> T createHelper(@Nullable ClassLoader targetClassLoader, List<String> helperClasses) {
        try {
            Class<T> helperClass;
            try {
                helperClass = loadHelperClass(targetClassLoader, helperClasses);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // in the unit tests, the agent is not added to the bootstrap class loader
                helperClass = loadHelperClass(ClassLoader.getSystemClassLoader(), helperClasses);
            }
            // the helper class may have a no-arg or a ElasticApmTracer constructor
            // this is preferable to a init method,
            // as it allows the tracer instance variable to be non-null
            try {
                return helperClass.getDeclaredConstructor(ElasticApmTracer.class).newInstance(tracer);
            } catch (NoSuchMethodException e) {
                return helperClass.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> loadHelperClass(@Nullable ClassLoader targetClassLoader, List<String> helperClasses) throws ClassNotFoundException, IOException {
        final ClassLoader helperCL = new ByteArrayClassLoader.ChildFirst(targetClassLoader, true, getTypeDefinitions(helperClasses));
        // the first helper class is the implementation of the interface
        return (Class<T>) helperCL.loadClass(helperClasses.get(0));
    }

    private Map<String, byte[]> getTypeDefinitions(List<String> helperClassNames) throws IOException {
        Map<String, byte[]> typeDefinitions = new HashMap<>();
        for (final String helperName : helperClassNames) {
            final ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(ClassLoader.getSystemClassLoader());
            final byte[] classBytes = locator.locate(helperName).resolve();
            typeDefinitions.put(helperName, classBytes);
        }
        return typeDefinitions;
    }

}
