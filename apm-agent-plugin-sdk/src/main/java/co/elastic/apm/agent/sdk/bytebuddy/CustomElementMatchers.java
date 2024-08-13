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
package co.elastic.apm.agent.sdk.bytebuddy;

import co.elastic.apm.agent.sdk.internal.InternalUtil;
import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class CustomElementMatchers {

    private static final Logger logger = LoggerFactory.getLogger(CustomElementMatchers.class);

    @Nullable
    private static final ClassLoader SELF_CLASS_LOADER = PrivilegedActionUtils.getClassLoader(CustomElementMatchers.class);

    private static final CustomElementMatchersProvider supplier = InternalUtil.getServiceProvider(CustomElementMatchersProvider.class);

    private static final ElementMatcher.Junction.AbstractBase<ClassLoader> AGENT_CLASS_LOADER_MATCHER = new ElementMatcher.Junction.AbstractBase<ClassLoader>() {
        @Override
        public boolean matches(@Nullable ClassLoader classLoader) {
            if (classLoader == SELF_CLASS_LOADER) {
                // This one also covers unit tests, where the app class loader loads the agent
                return true;
            }
            return supplier.isAgentClassLoader(classLoader);
        }
    };

    private static final ElementMatcher.Junction.AbstractBase<ClassLoader> INTERNAL_PLUGIN_CLASS_LOADER_MATCHER = new ElementMatcher.Junction.AbstractBase<ClassLoader>() {
        @Override
        public boolean matches(@Nullable ClassLoader classLoader) {
            return supplier.isInternalPluginClassLoader(classLoader);
        }
    };

    /**
     * Matches the target class loader to a given class loader by instance comparison
     *
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

    public static ElementMatcher.Junction<ClassLoader> isAgentClassLoader() {
        return AGENT_CLASS_LOADER_MATCHER;
    }

    public static ElementMatcher.Junction<ClassLoader> isInternalPluginClassLoader() {
        return INTERNAL_PLUGIN_CLASS_LOADER_MATCHER;
    }

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

    public static <T extends NamedElement> ElementMatcher.Junction<T> isProxy() {
        return nameContains("$Proxy")
            .or(nameContains("$$"))
            .or(nameContains("$JaxbAccessor"))
            .or(nameContains("CGLIB"))
            .or(nameContains("EnhancerBy"));
    }

    /**
     * A matcher that checks whether the implementation version read from the MANIFEST.MF related for a given {@link ProtectionDomain} is
     * lower than or equals to the limit version. Assumes a SemVer version format.
     *
     * @param version the version to check against
     * @return an LTE SemVer matcher
     */
    public static ElementMatcher.Junction<ProtectionDomain> implementationVersionLte(String version) {
        return implementationVersion(version, Matcher.LTE, null, null);
    }

    public static ElementMatcher.Junction<ProtectionDomain> implementationVersionGte(String version) {
        return implementationVersion(version, Matcher.GTE, null, null);
    }

    public static ElementMatcher.Junction<ProtectionDomain> implementationVersionLte(String version, String groupId, String artifactId) {
        return implementationVersion(version, Matcher.LTE, groupId, artifactId);
    }

    public static ElementMatcher.Junction<ProtectionDomain> implementationVersionGte(String version, String groupId, String artifactId) {
        return implementationVersion(version, Matcher.GTE, groupId, artifactId);
    }

    private static ElementMatcher.Junction<ProtectionDomain> implementationVersion(final String version, final Matcher matcher, @Nullable final String mavenGroupId, @Nullable final String mavenArtifactId) {
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
                    Version pdVersion = readImplementationVersion(protectionDomain, mavenGroupId, mavenArtifactId);
                    if (pdVersion != null) {
                        Version limitVersion = Version.of(version);
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

    @Nullable
    private static Version readImplementationVersion(@Nullable ProtectionDomain protectionDomain, @Nullable String mavenGroupId, @Nullable String mavenArtifactId) throws IOException, URISyntaxException {
        Version version = null;
        JarFile jarFile = null;

        if (protectionDomain == null) {
            logger.info("Cannot read implementation version - got null ProtectionDomain");
            return null;
        }

        try {
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

                    // read maven properties first as they have higher priority over manifest
                    // this is mostly for shaded libraries in "fat-jar" applications
                    if (mavenGroupId != null && mavenArtifactId != null) {
                        ZipEntry zipEntry = jarFile.getEntry(String.format("META-INF/maven/%s/%s/pom.properties", mavenGroupId, mavenArtifactId));
                        if (zipEntry != null) {
                            Properties properties = new Properties();
                            try (InputStream input = jarFile.getInputStream(zipEntry)) {
                                properties.load(input);
                            }
                            if (mavenGroupId.equals(properties.getProperty("groupId")) && mavenArtifactId.equals(properties.getProperty("artifactId"))) {
                                String stringVersion = properties.getProperty("version");
                                if (stringVersion != null) {
                                    version = Version.of(stringVersion);
                                }
                            }
                        }
                    }

                    // reading manifest if library packaged as a jar
                    //
                    // doing this after maven properties is important as it might report executable jar version
                    // when application is packaged as a "fat jar"
                    if (version == null) {
                        Manifest manifest = jarFile.getManifest();
                        if (manifest != null) {
                            Attributes attributes = manifest.getMainAttributes();
                            String manifestVersion = attributes.getValue("Implementation-Version");
                            if (manifestVersion == null) {
                                // fallback on OSGI bundle version when impl. version not available
                                manifestVersion = attributes.getValue("Bundle-Version");
                            }
                            if (manifestVersion != null) {
                                version = Version.of(manifestVersion);
                            }
                        }
                    }
                }
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

    public interface CustomElementMatchersProvider {

        boolean isAgentClassLoader(@Nullable ClassLoader classLoader);

        boolean isInternalPluginClassLoader(@Nullable ClassLoader classLoader);
    }
}
