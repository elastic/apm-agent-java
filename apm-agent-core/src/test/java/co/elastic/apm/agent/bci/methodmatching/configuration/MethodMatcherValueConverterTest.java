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
package co.elastic.apm.agent.bci.methodmatching.configuration;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import co.elastic.apm.agent.bci.methodmatching.MethodMatcher;
import co.elastic.apm.agent.configuration.converter.ListValueConverter;

import static org.assertj.core.api.Assertions.assertThat;

class MethodMatcherValueConverterTest {

    @Test
    void testAllExamplesAsList() {
        final ListValueConverter<MethodMatcher> tested = MethodMatcherValueConverter.LIST;

        List<MethodMatcher> result = tested.convert(
            "org.example.*, " +
                "org.example.*#*, " +
                "org.example.MyClass#myMethod, " +
                "org.example.MyClass#myMethod(), " +
                "org.example.MyClass#myMethod(java.lang.String), " +
                "org.example.MyClass#myMe*od(java.lang.String, int), " +
                "private org.example.MyClass#myMe*od(java.lang.String, *), " +
                "* org.example.MyClas*#myMe*od(*.String, int[]), " +
                "public org.example.services.*Service#*, " +
                "public @java.inject.ApplicationScoped org.example.*, " +
                "public @java.inject.* org.example.*, " +
                "public @@javax.enterprise.context.NormalScope org.example.*");

        assertThat(result.size()).isEqualTo(12);

        List<String> stringRepresentation = result.stream()
            .map(MethodMatcher::toString).collect(Collectors.toList());

        assertThat(stringRepresentation).isEqualTo(Arrays.asList(
            "org.example.*",
            "org.example.*#*",
            "org.example.MyClass#myMethod",
            "org.example.MyClass#myMethod()",
            "org.example.MyClass#myMethod(java.lang.String)",
            "org.example.MyClass#myMe*od(java.lang.String, int)",
            "private org.example.MyClass#myMe*od(java.lang.String, *)",
            "* org.example.MyClas*#myMe*od(*.String, int[])",
            "public org.example.services.*Service#*",
            "public @java.inject.ApplicationScoped org.example.*",
            "public @java.inject.* org.example.*",
            "public @@javax.enterprise.context.NormalScope org.example.*"
        ));
    }

}
