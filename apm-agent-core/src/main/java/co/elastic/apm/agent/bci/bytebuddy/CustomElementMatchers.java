/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.bci.bytebuddy;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URL;
import java.security.CodeSource;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.none;

public class CustomElementMatchers {

    private static final Logger logger = LoggerFactory.getLogger(CustomElementMatchers.class);

    public static ElementMatcher.Junction<NamedElement> isInAnyPackage(Collection<String> includedPackages,
                                                                       ElementMatcher.Junction<NamedElement> defaultIfEmpty) {
        if (includedPackages.isEmpty()) {
            return defaultIfEmpty;
        }
        ElementMatcher.Junction<NamedElement> matcher = none();
        for (String applicationPackage : includedPackages) {
            matcher = matcher.or(nameStartsWith(applicationPackage));
        }
        return matcher;
    }

    /**
     * Matches only class loaders which can load a certain class.
     * <p>
     * <b>Warning:</b> the class will be tried to load by each class loader.
     * You should choose a class which does not have optional dependencies (imports classes which are not on the class path).
     * Ideally, choose an interface or annotation without dependencies.
     * </p>
     *
     * @param className the name of the class to check
     * @return a matcher which only matches class loaders which can load a certain class.
     */
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
            if (target == null) {
                target = ClassLoader.getSystemClassLoader();
            }
            final URL resource = target.getResource(className.replace('.', '/') + ".class");
            result = resource != null;
            if (logger.isDebugEnabled()) {
                String classLoaderName = target.getClass().getName();
                String codeSourceString = "";
                if (resource != null) {
                    codeSourceString = " from " + resource;
                }
                logger.debug("{} was loaded by {}{}", className, classLoaderName, codeSourceString);
            }
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

    public static ElementMatcher.Junction<NamedElement> matches(final WildcardMatcher matcher) {
        return new ElementMatcher.Junction.AbstractBase<NamedElement>() {
            @Override
            public boolean matches(NamedElement target) {
                return matcher.matches(target.getActualName());
            }

            @Override
            public String toString() {
                return "matches(" + matcher + ")";
            }
        };
    }
}
