/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.matcher;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import specs.TestJsonSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static co.elastic.apm.agent.matcher.WildcardMatcher.indexOfIgnoreCase;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class WildcardMatcherTest {

    @ParameterizedTest
    @MethodSource("getJsonTestCases")
    void testJson(String testName, String pattern, Map<String, Boolean> expectedMatches) {
        final WildcardMatcher matcher = buildMatcher(pattern);
        expectedMatches.forEach((key, value) ->
            assertSoftly(softly ->
                softly.assertThat(matcher.matches(key))
                    .describedAs(testName)
                    .isEqualTo(value)));

    }

    private static Stream<Arguments> getJsonTestCases() {
        List<Arguments> arguments = new ArrayList<>();
        JsonNode json = TestJsonSpec.getJson("wildcard_matcher_tests.json");
        json.fieldNames().forEachRemaining(testName -> {
            JsonNode testNode = json.get(testName);
            String pattern = testNode.fieldNames().next();

            Map<String, Boolean> matchEntries = new LinkedHashMap<>();
            JsonNode patternNode = testNode.get(pattern);
            patternNode.fieldNames().forEachRemaining(name -> matchEntries.put(name, patternNode.get(name).asBoolean()));

            arguments.add(Arguments.of(testName, pattern, matchEntries));
        });
        return arguments.stream();
    }


    @Test
    void testWildcardInTheMiddle() {
        final WildcardMatcher matcher = buildMatcher("/foo/*/baz");
        assertSoftly(softly -> {
            softly.assertThat(matcher.matches("/foo/bar", "/baz")).isTrue();
            softly.assertThat(matcher.matches("/foo/bar/b", "az")).isTrue();
            softly.assertThat(matcher.matches("/foo/bar", "/boaz")).isFalse();
        });
    }

    @Test
    void testMatchBetween() {
        final WildcardMatcher matcher = buildMatcher("*foo*foo*");
        assertSoftly(softly -> {
            softly.assertThat(matcher.matches("foofo", "o")).isTrue();
            softly.assertThat(matcher.matches("foof", "oo")).isTrue();
            softly.assertThat(matcher.matches("foo", "foo")).isTrue();
            softly.assertThat(matcher.matches("fo", "ofoo")).isTrue();
            softly.assertThat(matcher.matches("f", "oofoo")).isTrue();
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
    void testMatchesPartitionedStringStartsWith() {
        final WildcardMatcher matcher = buildMatcher("/foo/bar*");
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
    void testMatchesPartitionedStringEndsWith() {
        final WildcardMatcher matcher = buildMatcher("*/bar/baz");
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
    void testMatchesInfixPartitionedString_allocationFree() {
        final WildcardMatcher matcher = buildMatcher("*foo*");
        assertSoftly(softly -> {
            // no allocations necessary
            softly.assertThat(matcher.matches("foo", "bar")).isTrue();
            softly.assertThat(matcher.matches("bar", "foo")).isTrue();
            softly.assertThat(matcher.matches("barfoo", "baz")).isTrue();
            softly.assertThat(matcher.matches("ba", "rfoo")).isTrue();
        });
    }

    @Test
    void testMatchesInfixPartitionedString_notAllocationFree() {
        final WildcardMatcher matcher = buildMatcher("*foo*");
        assertSoftly(softly -> {
            // requires concatenating the string
            softly.assertThat(matcher.matches("fo", "o")).isTrue();
            softly.assertThat(matcher.matches("fo", null)).isFalse();
            softly.assertThat(matcher.matches("barfo", "obaz")).isTrue();
            softly.assertThat(matcher.matches("bar", "baz")).isFalse();
        });
    }

    @Test
    void testMatchesNoWildcard() {
        final WildcardMatcher matcher = buildMatcher("foo");
        assertSoftly(softly -> {
            // requires concatenating the string
            softly.assertThat(matcher.matches("fo", "o")).isTrue();
            softly.assertThat(matcher.matches("foo", "bar")).isFalse();
        });
    }

    @Test
    void testMatchAnyStartsWith() {
        final WildcardMatcher matcher1 = buildMatcher("foo*");
        final WildcardMatcher matcher2 = buildMatcher("bar*");
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
    void testMatchesPartitionedStringStartsWith_ignoreCase() {
        final WildcardMatcher matcher = buildMatcher("/foo/bar*");
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
    void testMatchesPartitionedStringEndsWith_ignoreCase() {
        final WildcardMatcher matcher = buildMatcher("*/bar/baz");
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
    void testMatchesInfixPartitionedString_ignoreCase() {
        final WildcardMatcher matcher = buildMatcher("*foo*");
        assertSoftly(softly -> {
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
        final WildcardMatcher matcher = buildMatcher("foo");
        assertSoftly(softly -> {
            softly.assertThat(matcher.matches("FO", "O")).isTrue();
            softly.assertThat(matcher.matches("foO", "Bar")).isFalse();

        });
    }

    private WildcardMatcher buildMatcher(String pattern) {
        WildcardMatcher matcher = WildcardMatcher.valueOf(pattern);
        assertSoftly(softly -> {
            softly.assertThat(matcher.toString())
                .describedAs("wildcard matcher toString() should be equal to it's definition: %s", pattern)
                .isEqualTo(pattern);
        });
        return matcher;
    }

}
