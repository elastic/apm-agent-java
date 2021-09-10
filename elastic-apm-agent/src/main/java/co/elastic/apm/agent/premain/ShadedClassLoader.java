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
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ShadedClassLoader extends URLClassLoader {

    private static final String CLASS_EXTENSION = ".class";
    private final String prefix;
    private final String classFileExtension;
    private final Manifest manifest;
    private final URL agentJarUrl;

    static {
        ClassLoader.registerAsParallelCapable();
    }

    public ShadedClassLoader(File agentJar, ClassLoader parent, String prefix, String classNameExtension) throws IOException {
        super(new URL[]{agentJar.toURI().toURL()}, parent);
        this.prefix = prefix;
        this.classFileExtension = classNameExtension;
        this.agentJarUrl = agentJar.toURI().toURL();
        try (JarFile jarFile = new JarFile(agentJar)) {
            this.manifest = jarFile.getManifest();
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException e) {
            byte[] classBytes = getClassBytes(name);
            if (classBytes != null) {
                return defineClass(name, classBytes);
            } else {
                throw new ClassNotFoundException(name);
            }
        }
    }

    private Class<?> defineClass(String name, byte[] classBytes) {
        String packageName = getPackageName(name);
        if (packageName != null && getPackage(packageName) == null) {
            synchronized (this) {
                if (getPackage(packageName) == null) {
                    if (manifest != null) {
                        definePackage(name, manifest, agentJarUrl);
                    } else {
                        definePackage(packageName, null, null, null, null, null, null, null);
                    }
                }
            }
        }
        return defineClass(name, classBytes, 0, classBytes.length, ShadedClassLoader.class.getProtectionDomain());
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
        InputStream classStream = getResourceAsStream(prefix + name.replace('.', '/') + classFileExtension);
        if (classStream != null) {
            try (BufferedInputStream is = new BufferedInputStream(classStream)) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                int n;
                byte[] data = new byte[4 * 1024];
                while ((n = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, n);
                }
                return buffer.toByteArray();
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
        return null;
    }

    @Override
    public URL findResource(String name) {
        URL resource = super.findResource(getSharedResourceName(name));
        if (resource != null) {
            return resource;
        }
        return super.findResource(name);
    }

    private String getSharedResourceName(String name) {
        if (name.endsWith(CLASS_EXTENSION)) {
            return prefix + name.substring(0, name.length() - CLASS_EXTENSION.length()) + classFileExtension;
        } else {
            return prefix + name;
        }
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        Enumeration<URL> resources = super.findResources(getSharedResourceName(name));

        if (resources != null && resources.hasMoreElements()) {
            return resources;
        }
        return super.findResources(name);
    }
}
