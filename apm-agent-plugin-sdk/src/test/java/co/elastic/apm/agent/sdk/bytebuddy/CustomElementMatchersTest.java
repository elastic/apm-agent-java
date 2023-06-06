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
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.isInAnyPackage;
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
}
