/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.agent.matcher;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static co.elastic.apm.agent.matcher.WildcardMatcher.indexOfIgnoreCase;
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
    void testWildcardInTheMiddle() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("/foo/*/baz");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("/foo/*/baz");
            softly.assertThat(matcher.matches("/foo/bar/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo/bar", "/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo/bar/b", "az")).isTrue();
            softly.assertThat(matcher.matches("/foo/bar", "/boaz")).isFalse();
            softly.assertThat(matcher.matches("/foo/bar")).isFalse();
        });
    }

    @Test
    void testCompoundWildcardMatcher() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo*foo*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo*foo*");
            softly.assertThat(matcher.matches("foofoo")).isTrue();
            softly.assertThat(matcher.matches("foo/bar/foo")).isTrue();
            softly.assertThat(matcher.matches("/foo/bar/foo/bar")).isTrue();
            softly.assertThat(matcher.matches("foo")).isFalse();
        });
    }

    @Test
    void testCompoundWildcardMatcher3() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo*oo*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo*oo*");
            softly.assertThat(matcher.matches("foooo")).isTrue();
            softly.assertThat(matcher.matches("foofoo")).isTrue();
            softly.assertThat(matcher.matches("foo/bar/foo")).isTrue();
            softly.assertThat(matcher.matches("/foo/bar/foo/bar")).isTrue();
            softly.assertThat(matcher.matches("foo")).isFalse();
            softly.assertThat(matcher.matches("fooo")).isFalse();
        });
    }

    @Test
    void testCompoundWildcardMatcher2() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo*bar*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo*bar*");
            softly.assertThat(matcher.matches("foobar")).isTrue();
            softly.assertThat(matcher.matches("foo/bar/foo/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo/bar/baz")).isTrue();
            softly.assertThat(matcher.matches("bar/foo")).isFalse();
            softly.assertThat(matcher.matches("barfoo")).isFalse();
        });
    }

    @Test
    void testCompoundWildcardMatcher4() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo*far*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo*far*");
            softly.assertThat(matcher.matches("foofar")).isTrue();
            softly.assertThat(matcher.matches("foo/far/foo/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo/far/baz")).isTrue();
            softly.assertThat(matcher.matches("/far/foo")).isFalse();
            softly.assertThat(matcher.matches("farfoo")).isFalse();
        });
    }

    @Test
    void testMatchBetween() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo*foo*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo*foo*");
            softly.assertThat(matcher.matches("foofoo")).isTrue();
            softly.assertThat(matcher.matches("foofo", "o")).isTrue();
            softly.assertThat(matcher.matches("foof", "oo")).isTrue();
            softly.assertThat(matcher.matches("foo", "foo")).isTrue();
            softly.assertThat(matcher.matches("fo", "ofoo")).isTrue();
            softly.assertThat(matcher.matches("f", "oofoo")).isTrue();
            softly.assertThat(matcher.matches("foo/foo/foo/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo/foo/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo/foo")).isTrue();
            softly.assertThat(matcher.matches("foobar")).isFalse();
        });
    }

    @Test
    void testCharAt() {
        assertSoftly(softly -> {
            softly.assertThat(WildcardMatcher.SimpleWildcardMatcher.charAt(0, "foo", "bar", "foo".length())).isEqualTo('f');
            softly.assertThat(WildcardMatcher.SimpleWildcardMatcher.charAt(1, "foo", "bar", "foo".length())).isEqualTo('o');
            softly.assertThat(WildcardMatcher.SimpleWildcardMatcher.charAt(2, "foo", "bar", "foo".length())).isEqualTo('o');
            softly.assertThat(WildcardMatcher.SimpleWildcardMatcher.charAt(3, "foo", "bar", "foo".length())).isEqualTo('b');
            softly.assertThat(WildcardMatcher.SimpleWildcardMatcher.charAt(4, "foo", "bar", "foo".length())).isEqualTo('a');
            softly.assertThat(WildcardMatcher.SimpleWildcardMatcher.charAt(5, "foo", "bar", "foo".length())).isEqualTo('r');
        });
    }

    @Test
    void testIndexOfIgnoreCase() {
        assertSoftly(softly -> {
            softly.assertThat(indexOfIgnoreCase( "foo", "foo", "foo", false, 0, 6)).isEqualTo(0);
            softly.assertThat(indexOfIgnoreCase( "foo", "bar", "foo", false, 0, 6)).isEqualTo(0);
            softly.assertThat(indexOfIgnoreCase( "foo", "bar", "oob", false, 0, 6)).isEqualTo(1);
            softly.assertThat(indexOfIgnoreCase( "foo", "bar", "oba", false, 0, 6)).isEqualTo(2);
            softly.assertThat(indexOfIgnoreCase( "foo", "bar", "bar", false, 0, 6)).isEqualTo(3);
            softly.assertThat(indexOfIgnoreCase( "foo", "bar", "o", false, 0, 6)).isEqualTo(1);
            softly.assertThat(indexOfIgnoreCase( "foo", "bar", "ob", false, 0, 6)).isEqualTo(2);
            softly.assertThat(indexOfIgnoreCase( "foo", "bar", "ooba", false, 0, 6)).isEqualTo(1);
            softly.assertThat(indexOfIgnoreCase( "foo", "bar", "oobar", false, 0, 6)).isEqualTo(1);
            softly.assertThat(indexOfIgnoreCase( "foo", "bar", "fooba", false, 0, 6)).isEqualTo(0);
            softly.assertThat(indexOfIgnoreCase( "foo", "bar", "foobar", false, 0, 6)).isEqualTo(0);
            softly.assertThat(indexOfIgnoreCase( "afoo", "bar", "oba", false, 0, 7)).isEqualTo(3);
            softly.assertThat(indexOfIgnoreCase( "afoo", "bara", "oba", false, 0, 8)).isEqualTo(3);
            softly.assertThat(indexOfIgnoreCase( "aafoo", "baraa", "oba", false, 0, 10)).isEqualTo(4);

            softly.assertThat(indexOfIgnoreCase( "foo", "bar", "ara", false, 0, 6)).isEqualTo(-1);
        });
    }

    @Test
    void testComplexExpressions() {
        assertSoftly(softly -> {
            softly.assertThat(WildcardMatcher.valueOf("/foo/*/baz*").matches("/foo/a/bar/b/baz")).isTrue();
            softly.assertThat(WildcardMatcher.valueOf("/foo/*/bar/*/baz").matches("/foo/a/bar/b/baz")).isTrue();
        });
    }

    @Test
    void testInfixEmptyMatcher() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("**");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("**");
            softly.assertThat(matcher.matches("")).isTrue();
            softly.assertThat(matcher.matches("foo")).isTrue();
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
            softly.assertThat(matcher.matches("fo", null)).isFalse();
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
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "foo")).isEqualTo(matcher1);
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "bar")).isEqualTo(matcher2);
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "baz")).isNull();
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "fo", "o")).isEqualTo(matcher1);
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "ba", "r")).isEqualTo(matcher2);
            softly.assertThat(WildcardMatcher.anyMatch(Arrays.asList(matcher1, matcher2), "ba", "z")).isNull();
        });
    }

    @Test
    void testMatchesStartsWith_ignoreCase() {
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
    void testInfixEmptyMatcher_ignoreCase() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("**");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("**");
            softly.assertThat(matcher.matches("")).isTrue();
            softly.assertThat(matcher.matches("foo")).isTrue();
        });
    }

    @Test
    void testMatchesPartitionedStringStartsWith_ignoreCase() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("/foo/bar*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.matches("/foo/bAR/Baz", "")).isTrue();
            softly.assertThat(matcher.matches("", "/foo/bAR/baz")).isTrue();
            softly.assertThat(matcher.matches("/FOO/BAR", "/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo", "/BAR/BAZ")).isTrue();
            softly.assertThat(matcher.matches("/FOO", "/bar/baz")).isTrue();
            softly.assertThat(matcher.matches("/BAR", "/BAR/BAZ")).isFalse();
            softly.assertThat(matcher.matches("/FOO", "/foo/baz")).isFalse();
            softly.assertThat(matcher.matches("/foo/FOO", "/BAZ")).isFalse();
        });
    }

    @Test
    void testMatchesEndsWith_ignoreCase() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo");
            softly.assertThat(matcher.matches("fOo")).isTrue();
            softly.assertThat(matcher.matches("foobar")).isFalse();
            softly.assertThat(matcher.matches("bar")).isFalse();
            softly.assertThat(matcher.matches("baRFoo")).isTrue();
        });
    }

    @Test
    void testMatchesPartitionedStringEndsWith_ignoreCase() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*/bar/baz");
        assertSoftly(softly -> {
            softly.assertThat(matcher.matches("/foO/BAR/Baz", "")).isTrue();
            softly.assertThat(matcher.matches("", "/foO/Bar/baz")).isTrue();
            softly.assertThat(matcher.matches("/FOo/bar", "/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo", "/bar/BAZ")).isTrue();
            softly.assertThat(matcher.matches("/fOo", "/bAR/baz")).isTrue();
            softly.assertThat(matcher.matches("/bar", "/foO/baz")).isFalse();
            softly.assertThat(matcher.matches("/FOo", "/foo/baz")).isFalse();
        });
    }

    @Test
    void testMatchesEquals_ignoreCase() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("foo");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("foo");
            softly.assertThat(matcher.matches("fOo")).isTrue();
            softly.assertThat(matcher.matches("foOBar")).isFalse();
            softly.assertThat(matcher.matches("BAR")).isFalse();
            softly.assertThat(matcher.matches("barfoo")).isFalse();
        });
    }

    @Test
    void testMatchesInfix_ignoreCase() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo*");
            softly.assertThat(matcher.matches("FOO")).isTrue();
            softly.assertThat(matcher.matches("foOBar")).isTrue();
            softly.assertThat(matcher.matches("BAR")).isFalse();
            softly.assertThat(matcher.matches("baRFOo")).isTrue();
            softly.assertThat(matcher.matches("BARFOOBAZ")).isTrue();
        });
    }

    @Test
    void testMatchesInfix_caseSensitive() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("(?-i)*foo*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("(?-i)*foo*");
            softly.assertThat(matcher.matches("foo")).isTrue();
            softly.assertThat(matcher.matches("FOO")).isFalse();
        });
    }

    @Test
    void testMatchesInfixPartitionedString_ignoreCase() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("*foo*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("*foo*");
            // no allocations necessary
            softly.assertThat(matcher.matches("foo", "BAR")).isTrue();
            softly.assertThat(matcher.matches("BAR", "foo")).isTrue();
            softly.assertThat(matcher.matches("baRFoo", "baz")).isTrue();
            softly.assertThat(matcher.matches("bA", "Rfoo")).isTrue();
            softly.assertThat(matcher.matches("fo", "O")).isTrue();
            softly.assertThat(matcher.matches("barFO", "obaz")).isTrue();
            softly.assertThat(matcher.matches("bar", "baz")).isFalse();});
    }

    @Test
    void testMatchesNoWildcard_ignoreCase() {
        final WildcardMatcher matcher = WildcardMatcher.valueOf("foo");
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString()).isEqualTo("foo");
            softly.assertThat(matcher.matches("FO", "O")).isTrue();
            softly.assertThat(matcher.matches("FOO")).isTrue();
            softly.assertThat(matcher.matches("foO", "Bar")).isFalse();
            softly.assertThat(matcher.matches("foobar")).isFalse();

        });
    }


    @Test
    void testNeedleLongerThanHaystack() {
        assertSoftly(softly -> {
            softly.assertThat(WildcardMatcher.valueOf("*foo").matches("baz")).isFalse();
            softly.assertThat(WildcardMatcher.valueOf("*foob").matches("baz")).isFalse();
            softly.assertThat(WildcardMatcher.valueOf("*fooba").matches("baz")).isFalse();
            softly.assertThat(WildcardMatcher.valueOf("*foobar").matches("baz")).isFalse();
            softly.assertThat(WildcardMatcher.valueOf("foo*").matches("baz")).isFalse();
            softly.assertThat(WildcardMatcher.valueOf("foob*").matches("baz")).isFalse();
            softly.assertThat(WildcardMatcher.valueOf("fooba*").matches("baz")).isFalse();
            softly.assertThat(WildcardMatcher.valueOf("foobar*").matches("baz")).isFalse();
            softly.assertThat(WildcardMatcher.valueOf("*foobar*").matches("baz")).isFalse();
        });
    }

}
