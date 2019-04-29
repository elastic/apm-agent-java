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
package co.elastic.apm.agent.matcher;

import org.stagemonitor.util.StringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/*
 * Alternatives:
 *
 * Spring's AntPathMatcher:
 * This provides a more powerful glob-syntax to specify URL patterns.
 * The downside is that it allocates about 4bytes on every invocation,
 * depending on the concrete pattern.
 * Also, it is slower by a factor of ~100 compared to this class.
 *
 * Regular expressions:
 * Similar downsides to the AntPathMatcher -
 * it allocates objects on each invocation and has a higher latency.
 *
 * Elasticsearch's Regex#simpleMatch method:
 * Allocates objects while performing the match by calling {@link String#substring(int)},
 * which is something we want to avoid in the agent.
 * See https://github.com/elastic/elasticsearch/blob/v6.2.3/server/src/main/java/org/elasticsearch/common/regex/Regex.java#L87
 */

/**
 * This matcher is used in for example to disable tracing for certain URLs.
 * The advantage of this class compared to alternatives is is that {@linkplain #matches(String) matching} strings is completely allocation free.
 * <p>
 * The wildcard matcher supports the {@code *} wildcard which matches zero or more characters.
 * By default, matches are a case insensitive.
 * Single character wildcards like {@code f?o} are not supported.
 * </p>
 * <p>
 * The syntax should be very familiar to any developer.
 * The most common use cases are ignoring URLs paths starting with a specific string like {@code /resources/*} or <code>/heartbeat/*</code>
 * and ignoring URLs by file ending like {@code *.js}.
 * It also allows to have a single configuration option which,
 * depending on the input string,
 * allows for prefix, postfix and infix matching.
 * This implementation is also very fast,
 * as it just resorts to {@link String#startsWith(String)},
 * {@link String#endsWith(String)} and {@link String#contains(CharSequence)}.
 * </p>
 */
// don't use for-each as it allocates memory by instantiating an iterator
@SuppressWarnings("ForLoopReplaceableByForEach")
public abstract class WildcardMatcher {
    public static final String DOCUMENTATION = "This option supports the wildcard `*`, which matches zero or more characters.\n" +
        "Examples: `/foo/*/bar/*/baz*`, `*foo*`.\n" +
        "Matching is case insensitive by default.\n" +
        "Prepending an element with `(?-i)` makes the matching case sensitive.";
    private static final String CASE_INSENSITIVE_PREFIX = "(?i)";
    private static final String CASE_SENSITIVE_PREFIX = "(?-i)";
    private static final String WILDCARD = "*";
    private static final WildcardMatcher MATCH_ALL = valueOf(WILDCARD);

    public static WildcardMatcher caseSensitiveMatcher(String matcher) {
        return valueOf(CASE_SENSITIVE_PREFIX + matcher);
    }

    public static WildcardMatcher matchAll() {
        return MATCH_ALL;
    }

    /**
     * Constructs a new {@link WildcardMatcher} via a wildcard string.
     * <p>
     * It supports the {@code *} wildcard which matches zero or more characters.
     * </p>
     * <p>
     * By default, matches are a case insensitive.
     * Prepend {@code (?-i)} to your pattern to make it case sensitive.
     * Example: {@code (?-i)foo*} matches the string {@code foobar} but does not match {@code FOOBAR}.
     * </p>
     * <p>
     * It does NOT support single character wildcards like {@code f?o}
     * </p>
     *
     * @param wildcardString The wildcard string.
     * @return The {@link WildcardMatcher}
     */
    public static WildcardMatcher valueOf(final String wildcardString) {
        String matcher = wildcardString;
        boolean ignoreCase = true;
        if (matcher.startsWith(CASE_SENSITIVE_PREFIX)) {
            ignoreCase = false;
            matcher = matcher.substring(CASE_SENSITIVE_PREFIX.length());
        } else if (matcher.startsWith(CASE_INSENSITIVE_PREFIX)) {
            matcher = matcher.substring(CASE_INSENSITIVE_PREFIX.length());
        }

        String[] split = StringUtils.split(matcher, '*');
        if (split.length == 1) {
            return new SimpleWildcardMatcher(split[0], matcher.startsWith(WILDCARD), matcher.endsWith(WILDCARD), ignoreCase);
        }

        List<SimpleWildcardMatcher> matchers = new ArrayList<>(split.length);
        for (int i = 0; i < split.length; i++) {
            boolean isFirst = i == 0;
            boolean isLast = i == split.length - 1;
            matchers.add(new SimpleWildcardMatcher(split[i],
                !isFirst || matcher.startsWith(WILDCARD),
                !isLast || matcher.endsWith(WILDCARD),
                ignoreCase));
        }
        return new CompoundWildcardMatcher(wildcardString, matcher, matchers);
    }

    /**
     * Returns the first {@link WildcardMatcher} {@linkplain WildcardMatcher#matches(String) matching} the provided string.
     *
     * @param matchers the matchers which should be used to match the provided string
     * @param s        the string to match against
     * @return the first matching {@link WildcardMatcher}, or {@code null} if none match.
     */
    @Nullable
    public static boolean isAnyMatch(List<WildcardMatcher> matchers, @Nullable String s) {
        return anyMatch(matchers, s) != null;
    }

    /**
     * Returns {@code true}, if any of the matchers match the provided string.
     *
     * @param matchers the matchers which should be used to match the provided string
     * @param s        the string to match against
     * @return {@code true}, if any of the matchers match the provided string
     */
    @Nullable
    public static WildcardMatcher anyMatch(List<WildcardMatcher> matchers, @Nullable String s) {
        if (s == null) {
            return null;
        }
        return anyMatch(matchers, s, null);
    }

    /**
     * Returns the first {@link WildcardMatcher} {@linkplain WildcardMatcher#matches(String) matching} the provided partitioned string.
     *
     * @param matchers   the matchers which should be used to match the provided string
     * @param firstPart  The first part of the string to match against.
     * @param secondPart The second part of the string to match against.
     * @return the first matching {@link WildcardMatcher}, or {@code null} if none match.
     * @see #matches(String, String)
     */
    @Nullable
    public static WildcardMatcher anyMatch(List<WildcardMatcher> matchers, String firstPart, @Nullable String secondPart) {
        for (int i = 0; i < matchers.size(); i++) {
            if (matchers.get(i).matches(firstPart, secondPart)) {
                return matchers.get(i);
            }
        }
        return null;
    }

    /*
     * Based on https://stackoverflow.com/a/29809553/1125055
     * Thx to Zach Vorhies
     */
    public static int indexOfIgnoreCase(final String haystack1, final String haystack2, final String needle, final boolean ignoreCase, final int start, final int end) {
        if (start < 0) {
            return -1;
        }
        int totalHaystackLength = haystack1.length() + haystack2.length();
        if (needle.isEmpty() || totalHaystackLength == 0) {
            // Fallback to legacy behavior.
            return haystack1.indexOf(needle);
        }

        final int haystack1Length = haystack1.length();
        final int needleLength = needle.length();
        for (int i = start; i < end; i++) {
            // Early out, if possible.
            if (i + needleLength > totalHaystackLength) {
                return -1;
            }

            // Attempt to match substring starting at position i of haystack.
            int j = 0;
            int ii = i;
            while (ii < totalHaystackLength && j < needleLength) {
                char c = ignoreCase ? Character.toLowerCase(charAt(ii, haystack1, haystack2, haystack1Length)) : charAt(ii, haystack1, haystack2, haystack1Length);
                char c2 = ignoreCase ? Character.toLowerCase(needle.charAt(j)) : needle.charAt(j);
                if (c != c2) {
                    break;
                }
                j++;
                ii++;
            }
            // Walked all the way to the end of the needle, return the start
            // position that this was found.
            if (j == needleLength) {
                return i;
            }
        }
        return -1;
    }

    static char charAt(int i, String firstPart, String secondPart, int firstPartLength) {
        return i < firstPartLength ? firstPart.charAt(i) : secondPart.charAt(i - firstPartLength);
    }


    /**
     * Checks if the given string matches the wildcard pattern.
     *
     * @param s the String to match
     * @return whether the String matches the given pattern
     */
    public abstract boolean matches(String s);

    /**
     * This is a different version of {@link #matches(String)} which has the same semantics as calling
     * {@code matcher.matches(firstPart + secondPart);}.
     * <p>
     * The difference is that this method does not allocate memory.
     * </p>
     *
     * @param firstPart  The first part of the string to match against.
     * @param secondPart The second part of the string to match against.
     * @return {@code true},
     * when the wildcard pattern matches the partitioned string,
     * {@code false} otherwise.
     */
    public abstract boolean matches(String firstPart, @Nullable String secondPart);

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof WildcardMatcher)) {
            return false;
        }
        return toString().equals(obj.toString());
    }

    public abstract String getMatcher();

    /**
     * This {@link WildcardMatcher} supports wildcards in the middle of the matcher by decomposing the matcher into several
     * {@link SimpleWildcardMatcher}s.
     */
    static class CompoundWildcardMatcher extends WildcardMatcher {
        private final String wildcardString;
        private final String matcher;
        private final List<SimpleWildcardMatcher> wildcardMatchers;

        CompoundWildcardMatcher(String wildcardString, String matcher, List<SimpleWildcardMatcher> wildcardMatchers) {
            this.wildcardString = wildcardString;
            this.matcher = matcher;
            this.wildcardMatchers = wildcardMatchers;
        }

        @Override
        public boolean matches(String s) {
            int offset = 0;
            for (int i = 0; i < wildcardMatchers.size(); i++) {
                final SimpleWildcardMatcher matcher = wildcardMatchers.get(i);
                offset = matcher.indexOf(s, offset);
                if (offset == -1) {
                    return false;
                }
                offset += matcher.matcher.length();
            }
            return true;
        }

        @Override
        public boolean matches(String firstPart, @Nullable String secondPart) {
            int offset = 0;
            for (int i = 0; i < wildcardMatchers.size(); i++) {
                final SimpleWildcardMatcher matcher = wildcardMatchers.get(i);
                offset = matcher.indexOf(firstPart, secondPart, offset);
                if (offset == -1) {
                    return false;
                }
                offset += matcher.matcher.length();
            }
            return true;
        }

        @Override
        public String toString() {
            return wildcardString;
        }

        @Override
        public String getMatcher() {
            return matcher;
        }
    }

    /**
     * This {@link} does not support wildcards in the middle of a matcher.
     */
    static class SimpleWildcardMatcher extends WildcardMatcher {

        private final String matcher;
        private final String stringRepresentation;
        private final boolean wildcardAtEnd;
        private final boolean wildcardAtBeginning;
        private final boolean ignoreCase;

        SimpleWildcardMatcher(String matcher, boolean wildcardAtBeginning, boolean wildcardAtEnd, boolean ignoreCase) {
            this.matcher = matcher;
            this.wildcardAtEnd = wildcardAtEnd;
            this.wildcardAtBeginning = wildcardAtBeginning;
            this.ignoreCase = ignoreCase;
            this.stringRepresentation = new StringBuilder(matcher.length() + CASE_SENSITIVE_PREFIX.length() + WILDCARD.length() + WILDCARD.length())
                .append(ignoreCase ? "" : CASE_SENSITIVE_PREFIX)
                .append(wildcardAtBeginning ? WILDCARD : "")
                .append(matcher)
                .append(wildcardAtEnd ? WILDCARD : "")
                .toString();
        }

        @Override
        public String toString() {
            return stringRepresentation;
        }

        @Override
        public boolean matches(String s) {
            return indexOf(s, 0) != -1;
        }

        @Override
        public boolean matches(String firstPart, @Nullable String secondPart) {
            return indexOf(firstPart, secondPart, 0) != -1;
        }

        int indexOf(final String s, final int offset) {
            return indexOf(s, "", offset);
        }

        int indexOf(String firstPart, @Nullable String secondPart, int offset) {
            if (secondPart == null) {
                secondPart = "";
            }
            int totalLength = firstPart.length() + secondPart.length();
            if (wildcardAtEnd && wildcardAtBeginning) {
                return indexOfIgnoreCase(firstPart, secondPart, matcher, ignoreCase, offset, totalLength);
            } else if (wildcardAtEnd) {
                return indexOfIgnoreCase(firstPart, secondPart, matcher, ignoreCase, 0, 1);
            } else if (wildcardAtBeginning) {
                return indexOfIgnoreCase(firstPart, secondPart, matcher, ignoreCase, totalLength - matcher.length(), totalLength);
            } else if (totalLength == matcher.length()) {
                return indexOfIgnoreCase(firstPart, secondPart, matcher, ignoreCase, 0, totalLength);
            } else {
                return -1;
            }
        }

        @Override
        public String getMatcher() {
            return matcher;
        }
    }
}
