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
package co.elastic.apm.bci.bytebuddy;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.none;

public class CustomElementMatchers {

    public static ElementMatcher.Junction<TypeDescription> isInAnyPackage(Collection<String> includedPackages,
                                                                          ElementMatcher.Junction<TypeDescription> defaultIfEmpty) {
        if (includedPackages.isEmpty()) {
            return defaultIfEmpty;
        }
        ElementMatcher.Junction<TypeDescription> matcher = none();
        for (String applicationPackage : includedPackages) {
            matcher = matcher.or(nameStartsWith(applicationPackage));
        }
        return matcher;
    }

    public static ElementMatcher.Junction<ClassLoader> classLoaderCanLoadClass(final String className) {
        return new ElementMatcher.Junction.AbstractBase<ClassLoader>() {

            private final boolean loadableByBootstrapClassLoader = canLoadClass(null, className);
            private WeakConcurrentMap<ClassLoader, Boolean> cache = new WeakConcurrentMap.WithInlinedExpunction<>();

            @Override
            public boolean matches(@Nullable ClassLoader target) {
                if (target == null) {
                    return loadableByBootstrapClassLoader;
                }

                Boolean result = cache.get(target);
                if (result == null) {
                    result = canLoadClass(target, className);
                    cache.put(target, result);
                }
                return result;
            }
        };
    }

    private static boolean canLoadClass(@Nullable ClassLoader target, String className) {
        boolean result;
        try {
            Class.forName(className, false, target);
            result = true;
        } catch (Exception ignore) {
            result = false;
        }
        return result;
    }

    /**
     * Matches overridden methods of a super class or implemented methods of an interface.
     * Recursively traverses the superclasses and interfaces.
     * The the superclasses and interfaces to examine can be limited via {@link MethodHierarchyMatcher#onSuperClassesThat(ElementMatcher)}.
     *
     * @param methodElementMatcher The matcher which is applied on the method hierarchy
     * @return a matcher which is applied on the method hierarchy
     */
    public static MethodHierarchyMatcher overridesOrImplementsMethodThat(ElementMatcher<? super MethodDescription> methodElementMatcher) {
        return new MethodHierarchyMatcher(methodElementMatcher);
    }
}
