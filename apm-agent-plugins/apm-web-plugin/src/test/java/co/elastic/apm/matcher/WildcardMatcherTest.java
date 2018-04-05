/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.matcher;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class WildcardMatcherTest {

    @Test
    void testMatchesStartsWith() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("foo*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("foo*");
            softly.assertThat(matcher.matches("foo")).isTrue();
            softly.assertThat(matcher.matches("foobar")).isTrue();
            softly.assertThat(matcher.matches("bar")).isFalse();
            softly.assertThat(matcher.matches("barfoo")).isFalse();
        });
    }

    @Test
    void testMatchesPartitionedStringStartsWith() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("/foo/bar*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.matches("/foo/bar/baz", "")).isTrue();
            softly.assertThat(matcher.matches("", "/foo/bar/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo/bar", "/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo", "/bar/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo", "/bar/baz")).isTrue();
            softly.assertThat(matcher.matches("/bar", "/bar/baz")).isFalse();
            softly.assertThat(matcher.matches("/foo", "/foo/baz")).isFalse();
            softly.assertThat(matcher.matches("/foo/foo", "/baz")).isFalse();
        });
    }

    @Test
    void testMatchesEndsWith() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo");
            softly.assertThat(matcher.matches("foo")).isTrue();
            softly.assertThat(matcher.matches("foobar")).isFalse();
            softly.assertThat(matcher.matches("bar")).isFalse();
            softly.assertThat(matcher.matches("barfoo")).isTrue();
        });
    }

    @Test
    void testMatchesPartitionedStringEndsWith() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*/bar/baz");
        assertSoftly(softly -> {
            softly.assertThat(matcher.matches("/foo/bar/baz", "")).isTrue();
            softly.assertThat(matcher.matches("", "/foo/bar/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo/bar", "/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo", "/bar/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo", "/bar/baz")).isTrue();
            softly.assertThat(matcher.matches("/bar", "/foo/baz")).isFalse();
            softly.assertThat(matcher.matches("/foo", "/foo/baz")).isFalse();
        });
    }

    @Test
    void testMatchesEquals() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("foo");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("foo");
            softly.assertThat(matcher.matches("foo")).isTrue();
            softly.assertThat(matcher.matches("foobar")).isFalse();
            softly.assertThat(matcher.matches("bar")).isFalse();
            softly.assertThat(matcher.matches("barfoo")).isFalse();
        });
    }

    @Test
    void testMatchesInfix() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo*");
            softly.assertThat(matcher.matches("foo")).isTrue();
            softly.assertThat(matcher.matches("foobar")).isTrue();
            softly.assertThat(matcher.matches("bar")).isFalse();
            softly.assertThat(matcher.matches("barfoo")).isTrue();
            softly.assertThat(matcher.matches("barfoobaz")).isTrue();
        });
    }

    @Test
    void testMatchesInfixPartitionedString_allocationFree() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo*");
            // no allocations necessary
            softly.assertThat(matcher.matches("foo", "bar")).isTrue();
            softly.assertThat(matcher.matches("bar", "foo")).isTrue();
            softly.assertThat(matcher.matches("barfoo", "baz")).isTrue();
            softly.assertThat(matcher.matches("ba", "rfoo")).isTrue();
        });
    }

    @Test
    void testMatchesInfixPartitionedString_notAllocationFree() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo*");
            // requires concatenating the string
            softly.assertThat(matcher.matches("fo", "o")).isTrue();
            softly.assertThat(matcher.matches("barfo", "obaz")).isTrue();
            softly.assertThat(matcher.matches("bar", "baz")).isFalse();
        });
    }

    @Test
    void testMatchesNoWildcard() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("foo");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("foo");
            // requires concatenating the string
            softly.assertThat(matcher.matches("fo", "o")).isTrue();
            softly.assertThat(matcher.matches("foo")).isTrue();
            softly.assertThat(matcher.matches("foo", "bar")).isFalse();
            softly.assertThat(matcher.matches("foobar")).isFalse();

        });
    }

    @Test
    void testMatchAnyStartsWith() {
        final WildcardMatcher matcher1 = WildcardMatcher.valueOf("foo*");
        final WildcardMatcher matcher2 = WildcardMatcher.valueOf("bar*");
        assertSoftly(softly -> {
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "foo")).isTrue();
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "bar")).isTrue();
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "baz")).isFalse();
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "fo", "o")).isTrue();
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "ba", "r")).isTrue();
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "ba", "z")).isFalse();
        });
    }

}
