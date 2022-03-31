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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * A class loader that loads shaded class files form a jar file.
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
 */
public class ShadedClassLoader extends URLClassLoader {

    public static final String SHADED_CLASS_EXTENSION = ".esclazz";
    private static final String CLASS_EXTENSION = ".class";

    /**
     * Method handle for ClassLoader.getDefinedPackage that was added in Java 9
     */
    private static final MethodHandle getDefinedPackage;

    static {
        ClassLoader.registerAsParallelCapable();

        MethodHandle methodHandle = null;
        try {
            methodHandle = MethodHandles.lookup()
                .findSpecial(ClassLoader.class,
                    "getDefinedPackage",
                    MethodType.methodType(Package.class, String.class),
                    ShadedClassLoader.class);
        } catch (NoSuchMethodException e) {
            // ignored
        } catch (IllegalAccessException e){
            throw new IllegalStateException(e);
        }
        getDefinedPackage = methodHandle;
    }

    private final String customPrefix;
    private final Manifest manifest;
    private final URL jarUrl;

    public ShadedClassLoader(File jar, ClassLoader parent, String customPrefix) throws IOException {
        super(new URL[]{jar.toURI().toURL()}, parent);
        this.customPrefix = customPrefix;
        this.jarUrl = jar.toURI().toURL();
        try (JarFile jarFile = new JarFile(jar, false)) {
            this.manifest = jarFile.getManifest();
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classBytes = getClassBytes(name);
        if (classBytes != null) {
            return defineClass(name, classBytes);
        } else {
            throw new ClassNotFoundException(name);
        }
    }

    private Class<?> defineClass(String name, byte[] classBytes) {
        String packageName = getPackageName(name);
        if (packageName != null && isPackageNotDefined(packageName)) {
            try {
                if (manifest != null) {
                    definePackage(packageName, manifest, jarUrl);
                } else {
                    definePackage(packageName, null, null, null, null, null, null, null);
                }
            } catch (IllegalStateException e){
                // The package may have been defined by a parent class loader in the meantime
                if (isPackageNotDefined(packageName)) {
                    throw e;
                }
            }
        }
        return defineClass(name, classBytes, 0, classBytes.length, ShadedClassLoader.class.getProtectionDomain());
    }

    @SuppressWarnings("deprecation")
    private boolean isPackageNotDefined(String packageName){
        Package pkg;
        if(getDefinedPackage != null){
            try {
                pkg = (Package) getDefinedPackage.invokeExact(packageName);
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        } else {
            // use deprecated method for Java < 9
            pkg = getPackage(packageName);
        }
        return pkg != null;
    }

    @Nullable
    public String getPackageName(String className) {
        int i = className.lastIndexOf('.');
        if (i != -1) {
            return className.substring(0, i);
        }
        return null;
    }

    private byte[] getClassBytes(String name) throws ClassNotFoundException {
        try (InputStream is = getResourceAsStream(name.replace('.', '/') + CLASS_EXTENSION)) {
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

    @Override
    public URL findResource(String name) {
        // this class loader should only see classes and resources that start with the custom prefix
        // it still allows for classes and resources of the parent to be resolved via the getResource methods
        return super.findResource(getShadedResourceName(name));
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        return super.findResources(getShadedResourceName(name));
    }

    private String getShadedResourceName(String name) {
        if (name.endsWith(CLASS_EXTENSION)) {
            return customPrefix + name.substring(0, name.length() - CLASS_EXTENSION.length()) + SHADED_CLASS_EXTENSION;
        } else {
            return customPrefix + name;
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
