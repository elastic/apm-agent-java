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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// TODO docs (see co/elastic/apm/servlet/helper/package-info.java)
public class HelperClassManager {

    private final ElasticApmTracer tracer;
    private final Map<Class<?>, List<String>> helperClassDefinitions = new ConcurrentHashMap<>();
    // TODO use weak maps to prevent class loader leaks
    private final ConcurrentHashMap<ClassLoader, Map<Class<?>, Object>> helperClassCache = new ConcurrentHashMap<>();

    HelperClassManager(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    public void registerHelperClasses(Class<?> helperInterface, String implementation, String... additionalHelpers) {
        final ArrayList<String> helperClassNames = new ArrayList<>(additionalHelpers.length + 1);
        helperClassNames.add(implementation);
        helperClassNames.addAll(Arrays.asList(additionalHelpers));
        helperClassDefinitions.put(helperInterface, helperClassNames);
    }

    public <T extends AdviceHelper> T getHelperClass(ClassLoader targetClassLoader, Class<? super T> helperInterface) {
        if (!helperClassCache.containsKey(targetClassLoader)) {
            helperClassCache.putIfAbsent(targetClassLoader, new ConcurrentHashMap<Class<?>, Object>());
        }
        final Map<Class<?>, Object> helpersForCL = helperClassCache.get(targetClassLoader);
        if (!helpersForCL.containsKey(helperInterface)) {
            synchronized (helperInterface) {
                final T helper = createHelper(targetClassLoader, helperClassDefinitions.get(helperInterface));
                helpersForCL.put(helperInterface, helper);
            }
        }
        return (T) helpersForCL.get(helperInterface);
    }

    private <T extends AdviceHelper> T createHelper(ClassLoader targetClassLoader, List<String> helperClasses) {
        try {
            ClassLoader tracersCL = new ByteArrayClassLoader.ChildFirst(targetClassLoader, true, getTypeDefinitions(helperClasses));
            // the first helper class is the implementation of the interface
            Class<T> tracerClass = (Class<T>) tracersCL.loadClass(helperClasses.get(0));
            final T helper = tracerClass.getDeclaredConstructor().newInstance();
            helper.init(tracer);
            return helper;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    public interface AdviceHelper {
        void init(ElasticApmTracer tracer);
    }
}
