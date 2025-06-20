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

import net.bytebuddy.description.type.TypeDescription;
import org.apache.http.client.HttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.implementationVersionGte;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.implementationVersionLte;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.isAgentClassLoader;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.isInternalPluginClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.assertj.core.api.Assertions.assertThat;

class CustomElementMatchersTest {

    @Test
    void testSemVerLteWithFileUrl() {
        // Relying on Apache httpclient-4.5.6.jar
        testSemVerLteMatcher(HttpClient.class.getProtectionDomain());
    }

    @Test
    void testSemVerLteWithJarFileUrl() throws MalformedURLException {
        URL originalUrl = HttpClient.class.getProtectionDomain().getCodeSource().getLocation();
        URL jarFileUrl = new URL("jar:" + originalUrl.toString() + "!/");
        testSemVerLteMatcher(new ProtectionDomain(new CodeSource(jarFileUrl, new CodeSigner[0]), null));
    }

    @Test
    void testSemVerLteWithEncodedFileUrl() throws MalformedURLException, URISyntaxException {
        String jarFileUrl = new File("src/test/resources/lib/version##2/test-module.jar").toURI().toASCIIString();
        System.out.println("Encoded Jar file URL = " + jarFileUrl);
        ProtectionDomain protectionDomain = new ProtectionDomain(new CodeSource(new URL(jarFileUrl), new CodeSigner[0]), null);
        assertThat(implementationVersionLte("2").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("3").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("2.1.8").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("2.1.9").matches(protectionDomain)).isTrue();
    }

    @Test
    void testSemVerFallbackOnMavenProperties(@TempDir Path tempDir) throws URISyntaxException, IOException {
        // Relying on Apache httpclient-4.5.6.jar
        // creates a copy of the jar without the manifest so we should parse maven properties
        URL originalUrl = HttpClient.class.getProtectionDomain().getCodeSource().getLocation();
        Path modifiedJar = tempDir.resolve("test.jar");
        try {
            Files.copy(Paths.get(originalUrl.toURI()), modifiedJar);

            Map<String, String> fsProperties = new HashMap<>();
            fsProperties.put("create", "false");

            URI fsUri = URI.create("jar:" + modifiedJar.toUri());
            try (FileSystem zipFs = FileSystems.newFileSystem(fsUri, fsProperties)) {
                Files.delete(zipFs.getPath("META-INF", "MANIFEST.MF"));
            }

            ProtectionDomain protectionDomain = new ProtectionDomain(new CodeSource(modifiedJar.toUri().toURL(), new CodeSigner[0]), null);

            assertThat(implementationVersionLte("4.5.5", "org.apache.httpcomponents", "httpclient").matches(protectionDomain)).isFalse();
            assertThat(implementationVersionLte("4.5.6", "org.apache.httpcomponents", "httpclient").matches(protectionDomain)).isTrue();
            assertThat(implementationVersionGte("4.5.7", "org.apache.httpcomponents", "httpclient").matches(protectionDomain)).isFalse();


        } finally {
            if (Files.exists(modifiedJar)) {
                Files.delete(modifiedJar);
            }
        }

    }

    private void testSemVerLteMatcher(ProtectionDomain protectionDomain) {
        assertThat(implementationVersionLte("3").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("3.2").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("3.15.10").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("4.2.19").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("4.5.5").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("4.5.6").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("4.5.5-SNAPSHOT").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("4.5.6-SNAPSHOT").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("4.5.7").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("4.7.3").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("5.7.3").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("5.0").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("5").matches(protectionDomain)).isTrue();
    }

    @Test
    void testIncludedPackages() {
        final TypeDescription thisClass = TypeDescription.ForLoadedType.of(getClass());
        assertThat(isInAnyPackage(List.of(), none()).matches(thisClass)).isFalse();
        assertThat(isInAnyPackage(List.of(thisClass.getPackage().getName()), none()).matches(thisClass)).isTrue();
        assertThat(isInAnyPackage(List.of(thisClass.getPackage().getName()), none()).matches(TypeDescription.ForLoadedType.of(Object.class))).isFalse();
    }

    @Test
    void testClassLoaderCanLoadClass() {
        assertThat(classLoaderCanLoadClass(Object.class.getName()).matches(ClassLoader.getSystemClassLoader())).isTrue();
        assertThat(classLoaderCanLoadClass(Object.class.getName()).matches(null)).isTrue();
        assertThat(classLoaderCanLoadClass("not.Here").matches(ClassLoader.getSystemClassLoader())).isFalse();
    }

    @Test
    void testIsAgentClassLoader() {
        assertThat(isAgentClassLoader().matches(CustomElementMatchers.class.getClassLoader())).isTrue();
        assertThat(isAgentClassLoader().matches(new CustomElementMatchersTestProvider.SampleAgentClassLoader())).isTrue();
        assertThat(isAgentClassLoader().matches(new URLClassLoader(new URL[0]))).isFalse();
    }

    @Test
    void testIsInternalPluginClassLoader() {
        assertThat(isInternalPluginClassLoader().matches(CustomElementMatchers.class.getClassLoader())).isFalse();
        assertThat(isInternalPluginClassLoader().matches(new CustomElementMatchersTestProvider.SampleInternalPluginClassLoader())).isTrue();
        assertThat(isInternalPluginClassLoader().matches(new URLClassLoader(new URL[0]))).isFalse();
    }
}
