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
package co.elastic.apm.agent.bci.methodmatching;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class MethodMatcherInstrumentationTest {

    @Test
    void testMethodMatching() throws Exception {
        assertMatches(MethodMatcher.of(getClass().getName() + "#*"), getClass().getDeclaredMethod("testMethodMatching"));
        assertMatches(MethodMatcher.of(getClass().getName() + "#testMethodMatching"), getClass().getDeclaredMethod("testMethodMatching"));
        assertMatches(MethodMatcher.of(getClass().getName() + "#testMethodMatching()"), getClass().getDeclaredMethod("testMethodMatching"));
        assertMatches(MethodMatcher.of(getClass().getName() + "#testIntParameter"), getClass().getDeclaredMethod("testIntParameter", int.class));
        assertMatches(MethodMatcher.of("private " + getClass().getName() + "#testIntParameter"), getClass().getDeclaredMethod("testIntParameter", int.class));
        assertMatches(MethodMatcher.of("* " + getClass().getName() + "#testIntParameter"), getClass().getDeclaredMethod("testIntParameter", int.class));
        assertMatches(MethodMatcher.of(getClass().getName() + "#testIntParameter(int)"), getClass().getDeclaredMethod("testIntParameter", int.class));
        assertMatches(MethodMatcher.of(getClass().getName() + "#testStringParameter(java.lang.String)"), getClass().getDeclaredMethod("testStringParameter", String.class));
        assertMatches(MethodMatcher.of("protected " + getClass().getName() + "#testStringParameter(java.lang.String)"), getClass().getDeclaredMethod("testStringParameter", String.class));
        assertMatches(MethodMatcher.of("* " + getClass().getName() + "#testStringParameter(java.lang.String)"), getClass().getDeclaredMethod("testStringParameter", String.class));
        assertMatches(MethodMatcher.of(getClass().getName() + "#testMultipleParameters(java.lang.String, int[], java.lang.Object[])"), getClass().getDeclaredMethod("testMultipleParameters", String.class, int[].class, Object[].class));
        assertMatches(MethodMatcher.of(getClass().getName() + "#testMultipleParameters(*.String, int[], java.lang.Object[])"), getClass().getDeclaredMethod("testMultipleParameters", String.class, int[].class, Object[].class));
        assertMatches(MethodMatcher.of(getClass().getName() + "#testMultipleParameters(*, *, *)"), getClass().getDeclaredMethod("testMultipleParameters", String.class, int[].class, Object[].class));
    }

    @Test
    void testDoesNotMatch() throws Exception {
        assertDoesNotMatch(MethodMatcher.of(getClass().getName().toLowerCase() + "#testDoesNotMatch"), getClass().getDeclaredMethod("testDoesNotMatch"));
        assertDoesNotMatch(MethodMatcher.of(getClass().getName() + "#testdoesnotmatch"), getClass().getDeclaredMethod("testDoesNotMatch"));
        assertDoesNotMatch(MethodMatcher.of(getClass().getName() + "#DoesNot*"), getClass().getDeclaredMethod("testDoesNotMatch"));
    }

    private void testIntParameter(int i) {
    }

    protected void testStringParameter(String s) {
    }

    private void testMultipleParameters(String foo, int[] bar, Object... baz) {
    }

    private void assertDoesNotMatch(MethodMatcher methodMatcher, Method method) {
        assertThat(method).isNotNull();
        assertThat(methodMatcher).isNotNull();
        final TraceMethodInstrumentation methodMatcherInstrumentation = new TraceMethodInstrumentation(methodMatcher);
        assertThat(
            methodMatcherInstrumentation.getTypeMatcher().matches(TypeDescription.ForLoadedType.of(method.getDeclaringClass()))
                && methodMatcherInstrumentation.getMethodMatcher().matches(new MethodDescription.ForLoadedMethod(method)))
            .isFalse();
    }

    private void assertMatches(MethodMatcher methodMatcher, Method method) {
        assertThat(method).isNotNull();
        assertThat(methodMatcher).isNotNull();
        final TraceMethodInstrumentation methodMatcherInstrumentation = new TraceMethodInstrumentation(methodMatcher);
        assertThat(methodMatcherInstrumentation.getTypeMatcher().matches(TypeDescription.ForLoadedType.of(method.getDeclaringClass()))).isTrue();
        assertThat(methodMatcherInstrumentation.getMethodMatcher().matches(new MethodDescription.ForLoadedMethod(method))).isTrue();
    }
}
