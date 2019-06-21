/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.bci.bytebuddy;

import net.bytebuddy.description.type.TypeDescription;
import org.apache.http.client.HttpClient;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.List;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.implementationVersionLte;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.assertj.core.api.Assertions.assertThat;

class CustomElementMatchersTest {

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
}
