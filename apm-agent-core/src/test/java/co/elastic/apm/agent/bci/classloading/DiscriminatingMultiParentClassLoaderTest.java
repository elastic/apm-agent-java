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
package co.elastic.apm.agent.bci.classloading;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.example.TestClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;

import static co.elastic.apm.agent.bci.classloading.IndyPluginClassLoader.startsWith;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DiscriminatingMultiParentClassLoaderTest {

    private final Class<?> targetClass;
    private final ClassLoader targetClassLoader;
    private final ClassLoader agentClassLoader;
    private final DiscriminatingMultiParentClassLoader discriminatingMultiParentClassLoader;

    DiscriminatingMultiParentClassLoaderTest() throws IOException, ClassNotFoundException {
        // Constructing a target class loader outside the main application class loader hierarchy to load a test class of a non-agent packages
        String targetClassName = TestClass.class.getName();
        Map<String, byte[]> targetTypeDefinitions = Map.of(
            targetClassName,
            ClassFileLocator.ForClassLoader.of(ClassLoader.getSystemClassLoader()).locate(targetClassName).resolve()
        );
        targetClassLoader = new ByteArrayClassLoader.ChildFirst(null, true, targetTypeDefinitions, ByteArrayClassLoader.PersistenceHandler.MANIFEST);
        targetClass = targetClassLoader.loadClass(targetClassName);

        // never returns null
        agentClassLoader = ElasticApmAgent.getAgentClassLoader();

        discriminatingMultiParentClassLoader = new DiscriminatingMultiParentClassLoader(
            agentClassLoader, startsWith("co.elastic.apm.agent"),
            targetClassLoader, any());
    }

    @Test
    void testAgentClassLoading() throws ClassNotFoundException {
        Class<ElasticApmTracer> elasticApmTracerClass = ElasticApmTracer.class;
        assertThrows(ClassNotFoundException.class, () -> targetClassLoader.loadClass(elasticApmTracerClass.getName()));
        assertThat(discriminatingMultiParentClassLoader.loadClass(elasticApmTracerClass.getName())).isEqualTo(elasticApmTracerClass);
    }

    @Test
    void testTargetClassLoading() throws ClassNotFoundException {
        assertThat(agentClassLoader.loadClass(targetClass.getName())).isNotEqualTo(targetClass);
        assertThat(discriminatingMultiParentClassLoader.loadClass(targetClass.getName())).isEqualTo(targetClass);
    }

    @Test
    void testAgentClassResourceLookup() {
        String agentClassResourcePath = ElasticApmTracer.class.getName().replace('.', '/') + ".class";
        assertThat(targetClassLoader.getResource(agentClassResourcePath)).isNull();
        URL agentClassResource = agentClassLoader.getResource(agentClassResourcePath);
        assertThat(agentClassResource).isNotNull();
        assertThat(discriminatingMultiParentClassLoader.getResource(agentClassResourcePath)).isEqualTo(agentClassResource);
    }

    @Test
    void testTargetClassResourceLookup() {
        String targetClassResourcePath = targetClass.getName().replace('.', '/') + ".class";
        URL targetClassResource = targetClassLoader.getResource(targetClassResourcePath);
        assertThat(targetClassResource).isNotNull();
        assertThat(discriminatingMultiParentClassLoader.getResource(targetClassResourcePath)).isEqualTo(targetClassResource);

        // ensure that resource lookup was made through target CL, even though it is available in agent CL
        assertThat(agentClassLoader.getResource(targetClassResourcePath)).isNotNull();
        DiscriminatingMultiParentClassLoader parentWithoutTargetClassOnCP = new DiscriminatingMultiParentClassLoader(
            agentClassLoader, startsWith("co.elastic.apm.agent"),
            new URLClassLoader(new URL[0], null), any()
        );
        assertThat(parentWithoutTargetClassOnCP.getResource(targetClassResourcePath)).isNull();
    }

    @Test
    void testAgentNonClassResourceLookup() throws IOException {
        String testPropFile = "test.elasticapm.properties";
        assertThat(targetClassLoader.getResource(testPropFile)).isNull();
        assertThat(targetClassLoader.getResources(testPropFile).hasMoreElements()).isFalse();
        URL agentResource = agentClassLoader.getResource(testPropFile);
        assertThat(agentResource).isNotNull();
        Enumeration<URL> agentResources = agentClassLoader.getResources(testPropFile);
        assertThat(agentResources.hasMoreElements()).isTrue();
        URL resourceFromEnumeration = agentResources.nextElement();
        assertThat(discriminatingMultiParentClassLoader.getResources(testPropFile).nextElement()).isEqualTo(resourceFromEnumeration);
    }

    @Test
    void testNonAgentNonClassResourceLookup() throws IOException {
        Path tempResource = Files.createTempFile("resource-test", "tmp");
        ClassLoader resourceLoader = new URLClassLoader(new URL[]{tempResource.getParent().toUri().toURL()}, null);
        Path resourceFileName = tempResource.getFileName();
        assertThat(resourceLoader.getResource(resourceFileName.toString())).isNotNull();
        assertThat(resourceLoader.getResources(resourceFileName.toString()).hasMoreElements()).isTrue();
        assertThat(agentClassLoader.getResource(resourceFileName.toString())).isNull();
        assertThat(agentClassLoader.getResources(resourceFileName.toString()).hasMoreElements()).isFalse();
        DiscriminatingMultiParentClassLoader indyParentClWithResourcePath = new DiscriminatingMultiParentClassLoader(
            agentClassLoader, startsWith("co.elastic.apm.agent"),
            resourceLoader, any());
        assertThat(indyParentClWithResourcePath.getResource(resourceFileName.toString())).isNotNull();
        assertThat(indyParentClWithResourcePath.getResources(resourceFileName.toString()).hasMoreElements()).isTrue();
        Files.deleteIfExists(tempResource);
    }
}
