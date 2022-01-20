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
package co.elastic.apm.agent.bci.bytebuddy;

import co.elastic.apm.agent.matcher.AnnotationMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.util.ClassLoaderUtils;
import co.elastic.apm.agent.util.Version;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.none;

public class CustomElementMatchers {

    private static final Logger logger = LoggerFactory.getLogger(CustomElementMatchers.class);
    private static final ElementMatcher.Junction.AbstractBase<ClassLoader> AGENT_CLASS_LOADER_MATCHER = new ElementMatcher.Junction.AbstractBase<ClassLoader>() {
        @Override
        public boolean matches(@Nullable ClassLoader classLoader) {
            return ClassLoaderUtils.isAgentClassLoader(classLoader);
        }
    };

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
     * Matches the target class loader to a given class loader by instance comparison
     * @param other the class loader to match to
     * @return {@code true} if {@code other} is the same class loader instance as the target class loader
     */
    public static ElementMatcher.Junction<ClassLoader> isSameClassLoader(final ClassLoader other) {
        return new ElementMatcher.Junction.AbstractBase<ClassLoader>() {
            @Override
            public boolean matches(@Nullable ClassLoader target) {
                return target == other;
            }
        };
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
            private final WeakMap<ClassLoader, Boolean> cache = WeakConcurrent.buildMap();

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
        try {
            final URL resource;
            final String classResource = className.replace('.', '/') + ".class";
            if (target == null) {
                resource = Object.class.getResource("/" + classResource);
            } else {
                resource = target.getResource(classResource);
            }
            return resource != null;
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * A matcher that checks whether the implementation version read from the MANIFEST.MF related for a given {@link ProtectionDomain} is
     * lower than or equals to the limit version. Assumes a SemVer version format.
     *
     * @param version the version to check against
     * @return an LTE SemVer matcher
     */
    public static ElementMatcher.Junction<ProtectionDomain> implementationVersionLte(final String version) {
        return implementationVersion(version, Matcher.LTE);
    }

    public static ElementMatcher.Junction<ProtectionDomain> implementationVersionGte(final String version) {
        return implementationVersion(version, Matcher.GTE);
    }

    private static ElementMatcher.Junction<ProtectionDomain> implementationVersion(final String version, final Matcher matcher) {
        return new ElementMatcher.Junction.AbstractBase<ProtectionDomain>() {
            /**
             * Returns true if the implementation version read from the manifest file referenced by the given
             * {@link ProtectionDomain} is lower than or equal to the version set to this matcher
             *
             * @param protectionDomain a {@link ProtectionDomain} from which to look for the manifest file
             * @return true if version parsed from the manifest file is lower than or equals to the matcher's version
             *
             * NOTE: MAY RETURN FALSE POSITIVES - returns true if matching fails, logging a warning message
             */
            @Override
            public boolean matches(@Nullable ProtectionDomain protectionDomain) {
                try {
                    Version pdVersion = readImplementationVersionFromManifest(protectionDomain);
                    Version limitVersion = Version.of(version);
                    if (pdVersion != null) {
                        return matcher.match(pdVersion, limitVersion);
                    }
                } catch (Exception e) {
                    logger.info("Cannot read implementation version based on ProtectionDomain. This should not affect " +
                        "your agent's functionality. Failed with message: " + e.getMessage());
                    logger.debug("Implementation version parsing error: " + protectionDomain, e);
                }
                return true;
            }
        };
    }

    public static ElementMatcher.Junction<ClassLoader> isAgentClassLoader() {
        return AGENT_CLASS_LOADER_MATCHER;
    }

    private enum Matcher {
        LTE {
            @Override
            <T extends Comparable<T>> boolean match(T c1, T c2) {
                return c1.compareTo(c2) <= 0;
            }
        },
        GTE {
            @Override
            <T extends Comparable<T>> boolean match(T c1, T c2) {
                return c1.compareTo(c2) >= 0;

            }
        };
        abstract <T extends Comparable<T>> boolean match(T c1, T c2);
    }

    @Nullable
    private static Version readImplementationVersionFromManifest(@Nullable ProtectionDomain protectionDomain) throws IOException, URISyntaxException {
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
                            jarFile = new JarFile(new File(jarUrl.toURI()));
                        }
                        Manifest manifest = jarFile.getManifest();
                        if (manifest != null) {
                            String implementationVersion = manifest.getMainAttributes().getValue("Implementation-Version");
                            if (implementationVersion != null) {
                                version = Version.of(implementationVersion);
                            }
                        }
                    }
                }
            } else {
                logger.info("Cannot read implementation version - got null ProtectionDomain");
            }
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

    public static ElementMatcher.Junction<NamedElement> anyMatch(final List<WildcardMatcher> matchers) {
        return new ElementMatcher.Junction.AbstractBase<NamedElement>() {
            @Override
            public boolean matches(NamedElement target) {
                return WildcardMatcher.isAnyMatch(matchers, target.getActualName());
            }

            @Override
            public String toString() {
                return "matches(" + matchers + ")";
            }
        };
    }

    public static ElementMatcher.Junction<AnnotationSource> annotationMatches(final String annotationWildcard) {
        return AnnotationMatcher.annotationMatcher(annotationWildcard);
    }

    public static <T extends NamedElement> ElementMatcher.Junction<T> isProxy() {
        return nameContains("$Proxy")
            .or(nameContains("$$"))
            .or(nameContains("$JaxbAccessor"))
            .or(nameContains("CGLIB"))
            .or(nameContains("EnhancerBy"));
    }
}
