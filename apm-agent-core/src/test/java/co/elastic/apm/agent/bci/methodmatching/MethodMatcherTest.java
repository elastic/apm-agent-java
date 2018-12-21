package co.elastic.apm.agent.bci.methodmatching;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MethodMatcherTest {

    @Test
    void testMethodMatcherWithoutMethod() {
        assertThatThrownBy(() -> MethodMatcher.of("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMethodMatcherWithoutArguments() {
        final MethodMatcher methodMatcher = MethodMatcher.of("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest#testMethodMatcher");
        assertThat(methodMatcher).isNotNull();
        assertThat(methodMatcher.getClassMatcher().toString()).isEqualTo("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest");
        assertThat(methodMatcher.getMethodMatcher().toString()).isEqualTo("testMethodMatcher");
        assertThat(methodMatcher.getArgumentMatchers()).isNull();
    }

    @Test
    void testMethodMatcherNoArguments() {
        final MethodMatcher methodMatcher = MethodMatcher.of("public co.elastic.apm.agent.bci.methodmatching.Method*Test#testMethodMatcher()");
        assertThat(methodMatcher).isNotNull();
        assertThat(methodMatcher.getClassMatcher().toString()).isEqualTo("co.elastic.apm.agent.bci.methodmatching.Method*Test");
        assertThat(methodMatcher.getMethodMatcher().toString()).isEqualTo("testMethodMatcher");
        assertThat(methodMatcher.getArgumentMatchers()).isEmpty();
    }

    @Test
    void testMethodMatcherOneArg() {
        final MethodMatcher methodMatcher = MethodMatcher.of("private co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest#test*Matcher(java.lang.String)");
        assertThat(methodMatcher).isNotNull();
        assertThat(methodMatcher.getClassMatcher().toString()).isEqualTo("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest");
        assertThat(methodMatcher.getMethodMatcher().toString()).isEqualTo("test*Matcher");
        assertThat(methodMatcher.getArgumentMatchers()).hasSize(1);
        assertThat(methodMatcher.getArgumentMatchers()).contains(WildcardMatcher.valueOf("java.lang.String"));
    }

    @Test
    void testMethodMatcherTwoArgs() {
        final MethodMatcher methodMatcher = MethodMatcher.of("protected co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest#testMethodMatcher(*String, foo)");
        assertThat(methodMatcher).isNotNull();
        assertThat(methodMatcher.getClassMatcher().toString()).isEqualTo("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest");
        assertThat(methodMatcher.getMethodMatcher().toString()).isEqualTo("testMethodMatcher");
        assertThat(methodMatcher.getArgumentMatchers()).hasSize(2);
        assertThat(methodMatcher.getArgumentMatchers()).containsExactly(WildcardMatcher.valueOf("*String"), WildcardMatcher.valueOf("foo"));
    }

    @Test
    void testMethodMatcherThreeArgs() {
        final MethodMatcher methodMatcher = MethodMatcher.of("* co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest#testMethodMatcher(java.lang.String, foo,bar)");
        assertThat(methodMatcher).isNotNull();
        assertThat(methodMatcher.getClassMatcher().toString()).isEqualTo("co.elastic.apm.agent.bci.methodmatching.MethodMatcherTest");
        assertThat(methodMatcher.getMethodMatcher().toString()).isEqualTo("testMethodMatcher");
        assertThat(methodMatcher.getArgumentMatchers()).hasSize(3);
        assertThat(methodMatcher.getArgumentMatchers()).containsExactly(WildcardMatcher.valueOf("java.lang.String"), WildcardMatcher.valueOf("foo"), WildcardMatcher.valueOf("bar"));
    }
}
