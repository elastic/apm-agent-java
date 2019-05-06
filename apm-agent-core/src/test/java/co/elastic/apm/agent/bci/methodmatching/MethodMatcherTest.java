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
package co.elastic.apm.agent.bci.methodmatching;

import org.junit.jupiter.api.Test;

import static co.elastic.apm.agent.matcher.WildcardMatcher.caseSensitiveMatcher;
import static org.assertj.core.api.Assertions.assertThat;

class MethodMatcherTest {

    @Test
    void testMethodMatcherWithoutMethod() {
        final MethodMatcher methodMatcher = MethodMatcher.of("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest");
        assertThat(methodMatcher).isNotNull();
        assertThat(methodMatcher.getClassMatcher().getMatcher()).isEqualTo("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest");
        assertThat(methodMatcher.getMethodMatcher().getMatcher()).isEqualTo("*");
        assertThat(methodMatcher.getArgumentMatchers()).isNull();
    }

    @Test
    void testMethodMatcherWithoutArguments() {
        final MethodMatcher methodMatcher = MethodMatcher.of("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest#testMethodMatcher");
        assertThat(methodMatcher).isNotNull();
        assertThat(methodMatcher.getClassMatcher().getMatcher()).isEqualTo("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest");
        assertThat(methodMatcher.getMethodMatcher().getMatcher()).isEqualTo("testMethodMatcher");
        assertThat(methodMatcher.getArgumentMatchers()).isNull();
    }

    @Test
    void testMethodMatcherNoArguments() {
        final MethodMatcher methodMatcher = MethodMatcher.of("public co.elastic.apm.agent.bci.methodmatching.Method*Test#testMethodMatcher()");
        assertThat(methodMatcher).isNotNull();
        assertThat(methodMatcher.getClassMatcher().getMatcher()).isEqualTo("co.elastic.apm.agent.bci.methodmatching.Method*Test");
        assertThat(methodMatcher.getMethodMatcher().getMatcher()).isEqualTo("testMethodMatcher");
        assertThat(methodMatcher.getArgumentMatchers()).isEmpty();
    }

    @Test
    void testMethodMatcherOneArg() {
        final MethodMatcher methodMatcher = MethodMatcher.of("private co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest#test*Matcher(java.lang.String)");
        assertThat(methodMatcher).isNotNull();
        assertThat(methodMatcher.getClassMatcher().getMatcher()).isEqualTo("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest");
        assertThat(methodMatcher.getMethodMatcher().getMatcher()).isEqualTo("test*Matcher");
        assertThat(methodMatcher.getArgumentMatchers()).hasSize(1);
        assertThat(methodMatcher.getArgumentMatchers()).contains(caseSensitiveMatcher("java.lang.String"));
    }

    @Test
    void testMethodMatcherTwoArgs() {
        final MethodMatcher methodMatcher = MethodMatcher.of("protected co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest#testMethodMatcher(*String, foo)");
        assertThat(methodMatcher).isNotNull();
        assertThat(methodMatcher.getClassMatcher().getMatcher()).isEqualTo("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest");
        assertThat(methodMatcher.getMethodMatcher().getMatcher()).isEqualTo("testMethodMatcher");
        assertThat(methodMatcher.getArgumentMatchers()).hasSize(2);
        assertThat(methodMatcher.getArgumentMatchers()).containsExactly(caseSensitiveMatcher("*String"), caseSensitiveMatcher("foo"));
    }

    @Test
    void testMethodMatcherThreeArgs() {
        final MethodMatcher methodMatcher = MethodMatcher.of("* co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest#testMethodMatcher(java.lang.String, foo,bar)");
        assertThat(methodMatcher).isNotNull();
        assertThat(methodMatcher.getClassMatcher().getMatcher()).isEqualTo("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest");
        assertThat(methodMatcher.getMethodMatcher().getMatcher()).isEqualTo("testMethodMatcher");
        assertThat(methodMatcher.getArgumentMatchers()).hasSize(3);
        assertThat(methodMatcher.getArgumentMatchers()).containsExactly(caseSensitiveMatcher("java.lang.String"), caseSensitiveMatcher("foo"), caseSensitiveMatcher("bar"));
    }
}
