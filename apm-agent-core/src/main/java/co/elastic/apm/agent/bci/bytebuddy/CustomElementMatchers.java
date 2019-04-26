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
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static net.bytebuddy.matcher.ElementMatchers.nameContains;
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
            final URL resource;
            final String classResource = className.replace('.', '/') + ".class";
            if (target == null) {
                resource = Object.class.getResource("/" + classResource);
            } else {
                resource = target.getResource(classResource);
            }
            result = resource != null;
            if (logger.isDebugEnabled()) {
                String classLoaderName = (target == null) ? "Bootstrap ClassLoader" : target.getClass().getName();
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
     * A matcher that checks whether the implementation version read from the MANIFEST.MF related for a given {@link ProtectionDomain} is
     * lower than or equals to the limit version. Assumes a SemVer version format.
     * @param version the version to check against
     * @return an LTE SemVer matcher
     */
    public static ElementMatcher.Junction<ProtectionDomain> implementationVersionLte(final String version) {
        return new ElementMatcher.Junction.AbstractBase<ProtectionDomain>() {
            @Override
            public boolean matches(@Nullable ProtectionDomain protectionDomain) {
                Version pdVersion = readImplementationVersionFromManifest(protectionDomain);
                Version limitVersion = new Version(version);
                if (pdVersion != null) {
                    return pdVersion.compareTo(limitVersion) <= 0;
                }
                return false;
            }
        };
    }

    private static @Nullable Version readImplementationVersionFromManifest(@Nullable ProtectionDomain protectionDomain) {
        Version version = null;
        JarFile jarFile = null;
        try {
            if (protectionDomain != null) {
                CodeSource codeSource = protectionDomain.getCodeSource();
                if (codeSource != null) {
                    URL jarUrl = codeSource.getLocation();
                    if (jarUrl != null) {
                        // does not yet establish an actual connection
                        URLConnection urlConnection = jarUrl.openConnection();
                        if (urlConnection instanceof JarURLConnection) {
                            jarFile = ((JarURLConnection) urlConnection).getJarFile();
                        } else {
                            jarFile = new JarFile(jarUrl.getFile());
                        }
                        Manifest manifest = jarFile.getManifest();
                        if (manifest != null) {
                            String implementationVersion = manifest.getMainAttributes().getValue("Implementation-Version");
                            if (implementationVersion != null) {
                                version = new Version(implementationVersion);
                            }
                        }
                    }
                }
            } else {
                logger.error("Cannot read implementation version - got null ProtectionDomain");
            }
        } catch (Exception e) {
            logger.error("Cannot read implementation version based on ProtectionDomain " + protectionDomain, e);
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    logger.error("Error closing JarFile", e);
                }
            }
        }
        return version;
    }

    /**
     * Based on <a href="https://gist.github.com/brianguertin/ada4b65c6d1c4f6d3eee3c12b6ce021b">https://gist.github.com/brianguertin</a>
     */
    private static class Version implements Comparable<Version> {
        private final int[] numbers;

        Version(String version) {
            final String[] parts = version.split("\\.");
            numbers = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                numbers[i] = Integer.valueOf(parts[i]);
            }
        }

        @Override
        public int compareTo(Version another) {
            final int maxLength = Math.max(numbers.length, another.numbers.length);
            for (int i = 0; i < maxLength; i++) {
                final int left = i < numbers.length ? numbers[i] : 0;
                final int right = i < another.numbers.length ? another.numbers[i] : 0;
                if (left != right) {
                    return left < right ? -1 : 1;
                }
            }
            return 0;
        }
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

    public static <T extends NamedElement> ElementMatcher.Junction<T> isProxy() {
        return nameContains("$Proxy").or(nameContains("$$"));
    }
}
