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

import net.bytebuddy.description.type.TypeDescription;
import org.apache.http.client.HttpClient;
import org.junit.jupiter.api.Test;

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
    void testSemVerLteMatcher() {
        // Relying on Apache httpclient-4.5.6.jar
        ProtectionDomain protectionDomain = HttpClient.class.getProtectionDomain();
        assertThat(implementationVersionLte("3").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("3.2").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("3.15.10").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("4.2.19").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("4.5.5").matches(protectionDomain)).isFalse();
        assertThat(implementationVersionLte("4.5.6").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("4.5.7").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("4.7.3").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("5.7.3").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("5.0").matches(protectionDomain)).isTrue();
        assertThat(implementationVersionLte("5").matches(protectionDomain)).isTrue();
    }
}
