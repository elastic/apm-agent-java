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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static co.elastic.apm.agent.premain.ShadedClassLoader.SHADED_CLASS_EXTENSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class ShadedClassLoaderTest {

    @Test
    void testLoadShadedClass(@TempDir File tmp) throws Exception {
        File jar = createJar(tmp, List.of(ShadedClassLoaderTest.class), "agent/", SHADED_CLASS_EXTENSION);
        ClassLoader cl = new ShadedClassLoader(jar, null, "agent/");
        Class<?> clazz = cl.loadClass(ShadedClassLoaderTest.class.getName());
        assertThat(clazz).isNotNull();
        assertThat(clazz).isNotSameAs(ShadedClassLoaderTest.class);
        ((URLClassLoader) cl).close();
    }

    @Test
    void testLoadClassFromParent(@TempDir File tmp) throws Exception {
        File jar = createJar(tmp, List.of(ShadedClassLoaderTest.class), "agent/", SHADED_CLASS_EXTENSION);
        ClassLoader cl = new ShadedClassLoader(jar, ShadedClassLoaderTest.class.getClassLoader(), "agent/");
        Class<?> clazz = cl.loadClass(ShadedClassLoaderTest.class.getName());
        assertThat(clazz).isNotNull();
        assertThat(clazz).isSameAs(ShadedClassLoaderTest.class);
    }

    @Test
    void testCannotLoadNonShadedClass(@TempDir File tmp) throws Exception {
        File jar = createJar(tmp, List.of(ShadedClassLoaderTest.class), "", ".class");
        ClassLoader cl = new ShadedClassLoader(jar, null, "agent/");
        assertThatThrownBy(() -> cl.loadClass(ShadedClassLoaderTest.class.getName()))
            .isInstanceOf(ClassNotFoundException.class);
        ((URLClassLoader) cl).close();
    }

    @Test
    void testGetShadedResource(@TempDir File tmp) throws Exception {
        File jar = createJar(tmp, List.of(ShadedClassLoaderTest.class), "agent/", ".resource");
        ClassLoader cl = new ShadedClassLoader(jar, null, "agent/");
        byte[] expected = ClassLoader.getSystemClassLoader().getResourceAsStream(ShadedClassLoaderTest.class.getName().replace('.', '/') + ".class").readAllBytes();
        String resourceName = ShadedClassLoaderTest.class.getName().replace('.', '/') + ".resource";

        assertThat(cl.getResourceAsStream(resourceName).readAllBytes()).isEqualTo(expected);
        assertThat(cl.getResourceAsStream(resourceName).readAllBytes()).isEqualTo(expected);
        assertThat(cl.getResource(resourceName).openStream().readAllBytes()).isEqualTo(expected);
        assertThat(cl.getResources(resourceName).hasMoreElements()).isTrue();
        assertThat(cl.getResources(resourceName).nextElement().openStream().readAllBytes()).isEqualTo(expected);
        ((URLClassLoader) cl).close();
    }

    @Test
    void testCannotGetNonShadedResource(@TempDir File tmp) throws Exception {
        File jar = createJar(tmp, List.of(ShadedClassLoaderTest.class), "", ".resource");
        ClassLoader cl = new ShadedClassLoader(jar, null, "agent/");
        String resourceName = ShadedClassLoaderTest.class.getName().replace('.', '/') + ".resource";

        assertThat(cl.getResourceAsStream(resourceName)).isNull();
        ((URLClassLoader) cl).close();
    }

    @Test
    void testGetParentResource(@TempDir File tmp) throws Exception {
        File jar = createEmptyJar(tmp);
        ClassLoader cl = new ShadedClassLoader(jar, ShadedClassLoaderTest.class.getClassLoader(), "agent/");
        String resourceName = ShadedClassLoaderTest.class.getName().replace('.', '/') + ".class";
        byte[] expected = ShadedClassLoaderTest.class.getClassLoader().getResourceAsStream(resourceName).readAllBytes();

        assertThat(cl.getResourceAsStream(resourceName).readAllBytes()).isEqualTo(expected);
        assertThat(cl.getResourceAsStream(resourceName).readAllBytes()).isEqualTo(expected);
        assertThat(cl.getResource(resourceName).openStream().readAllBytes()).isEqualTo(expected);
        assertThat(cl.getResources(resourceName).hasMoreElements()).isTrue();
        assertThat(cl.getResources(resourceName).nextElement().openStream().readAllBytes()).isEqualTo(expected);
    }

    private File createEmptyJar(File tmp) throws IOException {
        return createJar(tmp, List.of(), "", ".class");
    }

    private File createJar(File folder, List<Class<?>> classes, String classNamePrefix, String classNameExtension) throws IOException {
        File file = new File(folder, "test.jar");
        assertThat(file.createNewFile()).isTrue();
        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(file))) {
            for (Class<?> clazz : classes) {
                jarOutputStream.putNextEntry(new JarEntry(classNamePrefix + clazz.getName().replace('.', '/') + classNameExtension));
                jarOutputStream.write(clazz.getResourceAsStream(clazz.getSimpleName() + ".class").readAllBytes());
            }
        }
        return file;
    }

}
