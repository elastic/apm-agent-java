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
package co.elastic.apm.agent.premain;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * A class loader that loads shaded class files form a jar file.
 * Only shaded classes can be loaded by this class loader. Other resources will be looked up even both in the shading directory and
 * in the root directory
 * <p>
 * Shaded classes are hidden from normal class loaders.
 * A regular class is packaged like this in a jar: {@code org/example/MyClass.class}
 * A shaded class is packaged like this in a jar: {@code agent/org/example/MyClass.esclazz}
 * </p>
 * <p>
 * This is used to hide the classes of the agent from the regular class loader hierarchy.
 * The main agent is loaded by this class loader in an isolated hierarchy that the application/system class loader doesn't have access to.
 * This is to minimize the impact the agent has on the application and to avoid tools like classpath scanners from tripping over the agent.
 * </p>
 * <p>
 * Another benefit is that if all instrumentations are reverted and all references to the agent are removed,
 * the agent class loader, along with its loaded classes, can be unloaded.
 * </p>
 * <p>
 * This class loader is working as a child-first, meaning it would first lookup classes locally in their shaded form and only if not found,
 * will delegate lookup to parent in the normal form.
 * </p>
 */
public class ShadedClassLoader extends URLClassLoader {

    public static final String SHADED_CLASS_EXTENSION = ".esclazz";
    private static final String CLASS_EXTENSION = ".class";

    private static final ProtectionDomain PROTECTION_DOMAIN;

    static {
        ClassLoader.registerAsParallelCapable();

        if (System.getSecurityManager() == null) {
            PROTECTION_DOMAIN = ShadedClassLoader.class.getProtectionDomain();
        } else {
            PROTECTION_DOMAIN = AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>() {
                @Override
                public ProtectionDomain run() {
                    return ShadedClassLoader.class.getProtectionDomain();
                }
            });
        }
    }

    private final String customPrefix;
    private final Manifest manifest;
    private final URL jarUrl;
    private final ThreadLocal<Set<String>> locallyNonAvailableResources = new ThreadLocal<Set<String>>() {
        @Override
        protected Set<String> initialValue() {
            return new HashSet<>();
        }
    };

    public ShadedClassLoader(File jar, ClassLoader parent, String customPrefix) throws IOException {
        super(new URL[]{jar.toURI().toURL()}, parent);
        this.customPrefix = customPrefix;
        this.jarUrl = jar.toURI().toURL();
        try (JarFile jarFile = new JarFile(jar, false)) {
            this.manifest = jarFile.getManifest();
        }
    }

    /**
     * A child-first implementation for class loading by searching for classes in the following order:
     *
     * <ol>
     *   <li><p> Invoke {@link #findLoadedClass(String)} to check if the class
     *   has already been loaded.  </p></li>
     *
     *   <li><p> Invoke {@link #findClass(String)} to find the
     *   class locally. The way to guarantee only local lookup is by searching only for the
     *   shaded form. </p></li>
     *
     *   <li><p> If not found so far, invoke the {@link ClassLoader#loadClass(String) loadClass}
     *   method. This will start a parent-first lookup, eventually looking locally through
     *   {@link ClassLoader#findClass(String)}, which will only do the normal {@link URLClassLoader#ucp}
     *   lookup, which can only find a limited number of classes used for the agent very early initialization.
     *   Note that this would also cause a redundant additional {@link ClassLoader#getResource(String)}
     *   lookup, but we guard from that through a {@link ThreadLocal}. </p></li>
     * </ol>
     *
     * @param name    The <a href="#binary-name">binary name</a> of the class
     * @param resolve If {@code true} then resolve the class
     * @return The resulting {@code Class} object
     * @throws ClassNotFoundException If the class could not be found
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            try {
                // First, check if the class has already been loaded
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    c = findClass(name);
                    if (resolve) {
                        resolveClass(c);
                    }
                }
                return c;
            } catch (ClassNotFoundException e) {
                return super.loadClass(name, resolve);
            }
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classBytes = getShadedClassBytes(name);
        if (classBytes != null) {
            return defineClass(name, classBytes);
        }
        throw new ClassNotFoundException(name);
    }

    private Class<?> defineClass(String name, byte[] classBytes) {
        String packageName = getPackageName(name);
        if (packageName != null && !isPackageDefined(packageName)) {
            try {
                if (manifest != null) {
                    definePackage(packageName, manifest, jarUrl);
                } else {
                    definePackage(packageName, null, null, null, null, null, null, null);
                }
            } catch (IllegalArgumentException e) {
                // The package may have been defined by a parent class loader in the meantime
                if (!isPackageDefined(packageName)) {
                    throw e;
                }
            }
        }

        return defineClass(name, classBytes, 0, classBytes.length, PROTECTION_DOMAIN);
    }

    @SuppressWarnings("deprecation")
    private boolean isPackageDefined(String packageName) {
        // The 'getPackage' method is deprecated as of Java 9, 'getDefinedPackage' is the alternative.
        //
        // The only difference is that 'getDefinedPackage' does not delegate to parent CL for lookup.
        // Given we are only interested on the fact that the package is defined or not without caring about which CL
        // has defined it, it does not make any difference in our case.
        return getPackage(packageName) != null;
    }

    @Nullable
    public String getPackageName(String className) {
        int i = className.lastIndexOf('.');
        if (i != -1) {
            return className.substring(0, i);
        }
        return null;
    }

    private byte[] getShadedClassBytes(String name) throws ClassNotFoundException {
        try (InputStream is = getPrivilegedResourceAsStream(customPrefix + name.replace('.', '/') + SHADED_CLASS_EXTENSION)) {
            if (is != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int n;
                byte[] data = new byte[1024];
                while ((n = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, n);
                }
                return buffer.toByteArray();
            }
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
        return null;
    }

    private InputStream getPrivilegedResourceAsStream(final String name) {
        if (System.getSecurityManager() == null) {
            return getResourceAsStreamInternal(name);
        }

        return AccessController.doPrivileged(new PrivilegedAction<InputStream>() {
            @Override
            public InputStream run() {
                return getResourceAsStreamInternal(name);
            }
        });
    }

    private InputStream getResourceAsStreamInternal(String name) {
        return super.getResourceAsStream(name);
    }

    /**
     * This class loader should only see classes and resources that start with the custom prefix.
     * It still allows for classes and resources of the parent to be resolved via the getResource methods
     *
     * @param name the name of the resource
     * @return a {@code URL} for the resource, or {@code null}
     * if the resource could not be found, or if the loader is closed.
     */
    @Override
    public URL findResource(final String name) {
        if (locallyNonAvailableResources.get().contains(name)) {
            return null;
        }

        if (System.getSecurityManager() == null) {
            return findResourceInternal(getShadedResourceName(name));
        }

        // while most of the body of default 'findResource' in JDK implementation is in a privileged action
        // an extra URL check is performed just after it, hence we have to wrap the whole method call in a privileged
        // action otherwise the security manager will complain about lack of proper read privileges on the agent jar
        return AccessController.doPrivileged(new PrivilegedAction<URL>() {
            @Override
            public URL run() {
                return findResourceInternal(getShadedResourceName(name));
            }
        });

    }

    private URL findResourceInternal(String name) {
        return super.findResource(name);
    }

    /**
     * This class loader should only see classes and resources that start with the custom prefix.
     * It still allows for classes and resources of the parent to be resolved via the getResource methods
     *
     * @param name the name of the resource
     * @return a {@code URL} for the resource, or {@code null}
     * if the resource could not be found, or if the loader is closed.
     */
    @Override
    public Enumeration<URL> findResources(final String name) throws IOException {
        if (locallyNonAvailableResources.get().contains(name)) {
            return Collections.emptyEnumeration();
        }

        Enumeration<URL> result = super.findResources(getShadedResourceName(name));
        if (System.getSecurityManager() == null) {
            return result;
        }

        return new PrivilegedEnumeration<URL>(result);
    }

    /**
     * Implements a child-first resource lookup in the following order:
     *
     * <ol>
     *   <li><p> Look locally, which can only be done in the shaded form (see {@link #findResource(String)}). </p></li>
     *   <li><p> If not found, invoke the super's {@link URLClassLoader#getResource(String) getResource()}
     *   using the original form in a parent-first manner. This causes a duplicated lookup, but it's preferable over trying to
     *   deal with invoking lookup on the parent, where the parent may be {@code null} and not easily
     *   accessible from here. </p></li>
     * </ol>
     */
    @Override
    public URL getResource(String name) {
        // look locally first
        URL shadedResource = findResource(name);
        if (shadedResource != null) {
            return shadedResource;
        }
        // if not found locally, calling super's lookup, which does parent first and then local, so marking as not required for local lookup
        Set<String> locallyNonAvailableResources = this.locallyNonAvailableResources.get();
        try {
            locallyNonAvailableResources.add(name);
            return super.getResource(name);
        } finally {
            locallyNonAvailableResources.remove(name);
        }
    }

    /**
     * Implements a child-first resources lookup in the following order:
     *
     * <ol>
     *   <li><p> Look locally, which can only be done in the shaded form (see {@link #findResource(String)}). </p></li>
     *   <li><p> If not found, invoke the super's {@link URLClassLoader#getResources(String) getResources()}
     *   using the original form in a parent-first manner. This causes a duplicated lookup, but it's preferable over trying to
     *   deal with invoking lookup on the parent, where the parent may be {@code null} and not easily
     *   accessible from here. </p></li>
     * </ol>
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        // look locally first
        Enumeration<URL> shadedResources = findResources(name);
        if (shadedResources.hasMoreElements()) {
            // no need to compound results with parent lookup, we only want to return the shaded form if there is such
            return shadedResources;
        }
        // if not found locally, calling super's lookup, which does parent first and then local, so marking as not required for local lookup
        Set<String> locallyNonAvailableResources = this.locallyNonAvailableResources.get();
        try {
            locallyNonAvailableResources.add(name);
            return super.getResources(name);
        } finally {
            locallyNonAvailableResources.remove(name);
        }
    }

    private String getShadedResourceName(String name) {
        if (name.startsWith(customPrefix)) {
            // already a lookup of the shaded form
            return name;
        } else if (name.endsWith(CLASS_EXTENSION)) {
            return customPrefix + name.substring(0, name.length() - CLASS_EXTENSION.length()) + SHADED_CLASS_EXTENSION;
        } else {
            return customPrefix + name;
        }
    }

    /**
     * Wraps an {@link Enumeration} with privileged actions when executed with a security manager
     *
     * @param <E>
     */
    private static class PrivilegedEnumeration<E> implements Enumeration<E> {

        private final Enumeration<E> delegate;

        private PrivilegedEnumeration(Enumeration<E> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasMoreElements() {
            return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                    return delegate.hasMoreElements();
                }
            });
        }

        @Override
        public E nextElement() {
            return AccessController.doPrivileged(new PrivilegedAction<E>() {
                @Override
                public E run() {
                    return delegate.nextElement();
                }
            });
        }
    }

    @Override
    public String toString() {
        return "ShadedClassLoader{" +
            "parent=" + getParent() +
            ", customPrefix='" + customPrefix + '\'' +
            ", manifest=" + manifest +
            ", jarUrl=" + jarUrl +
            '}';
    }
}
