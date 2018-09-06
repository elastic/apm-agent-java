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
package co.elastic.apm.matcher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * This matcher is used in for example to disable tracing for certain URLs.
 * <p>
 * The advantage of this class is that it does not instantiate objects when calling {@link #matches(String)}.
 * The syntax should be very familiar to any developer.
 * The only thing that might be surprising is that it does not support wildcards in between like {@code foo*bar}.
 * However, this provides support for the most common use cases -
 * ignoring URLs paths starting with a specific string like {@code /resources/*} or <code>/heartbeat/*</code>
 * and ignoring URLs by file ending like {@code *.js}.
 * It also allows to have a single configuration option which,
 * depending on the input string,
 * allows for prefix, postfix and infix matching.
 * This implementation is also very fast,
 * as it just resorts to {@link String#startsWith(String)},
 * {@link String#endsWith(String)} and {@link String#contains(CharSequence)}.
 * </p>
 */
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
 * It works quite similar to this class but also supports wildcards in the middle.
 * However, it also allocates objects while performing the match by calling {@link String#substring(int)},
 * which is something we want to avoid in the agent.
 * See https://github.com/elastic/elasticsearch/blob/v6.2.3/server/src/main/java/org/elasticsearch/common/regex/Regex.java#L87
 */
// don't use for-each as it allocates memory by instantiating an iterator
@SuppressWarnings("ForLoopReplaceableByForEach")
public abstract class WildcardMatcher {
    private static final String CASE_INSENSITIVE_PREFIX = "(?i)";
    private static final String WILDCARD = "*";
    private static final String INVALID_WILDCARD_MESSAGE = "A wildcard expression may either contain a single wildcard in the middle " +
        "or have wildcards at the beginning and/or the end. Legal: `*foo*`, `foo*`, `*foo`, `foo*bar*`. Illegal: `foo*bar*`, `foo*bar*baz`.";

    public static final String DOCUMENTATION = "\nNOTE: " + INVALID_WILDCARD_MESSAGE + "\n" +
        "Prepending an element with `(?i)` makes the matching case-insensitive.";

    /**
     * Constructs a new {@link WildcardMatcher} via a wildcard string.
     * <p>
     * Note that only wildcards at the beginning and at the end of the string are supported.
     * </p>
     * <p>
     * If you don't provide any wildcards,
     * {@link #matches(String)} has the same semantics as {@link String#equals(Object)}.
     * </p>
     *
     * @param wildcardString The wildcard string.
     * @return The {@link WildcardMatcher}
     */
    public static WildcardMatcher valueOf(final String wildcardString) {
        String matcher = wildcardString;
        boolean ignoreCase = false;
        if (matcher.startsWith(CASE_INSENSITIVE_PREFIX)) {
            ignoreCase = true;
            matcher = matcher.substring(CASE_INSENSITIVE_PREFIX.length(), matcher.length());
        }
        if (matcher.startsWith(WILDCARD) || matcher.endsWith(WILDCARD) || !matcher.contains(WILDCARD)) {
            return parseSimpleWildcardMatcher(wildcardString, matcher, ignoreCase);
        } else {
            String[] split = matcher.split("\\*");
            if (split.length != 2) {
                throw new IllegalArgumentException(INVALID_WILDCARD_MESSAGE);
            }
            return new CompoundWildcardMatcher(wildcardString, Arrays.asList(valueOf(split[0] + WILDCARD), valueOf(WILDCARD + split[1])));
        }
    }

    @Nonnull
    private static WildcardMatcher parseSimpleWildcardMatcher(String wildcardString, String matcher, boolean ignoreCase) {
        boolean startsWith = false;
        boolean endsWith = false;
        if (matcher.startsWith(WILDCARD)) {
            endsWith = true;
            matcher = matcher.substring(1, matcher.length());
        }
        if (matcher.endsWith(WILDCARD)) {
            startsWith = true;
            matcher = matcher.substring(0, matcher.length() - 1);
        }
        if (matcher.contains(WILDCARD)) {
            throw new IllegalArgumentException(INVALID_WILDCARD_MESSAGE);
        }
        return new SimpleWildcardMatcher(matcher, wildcardString, startsWith, endsWith, ignoreCase);
    }


    /**
     * Returns {@code true}, if any of the matchers match the provided string.
     *
     * @param matchers the matchers which should be used to match the provided string
     * @param s        the string to match against
     * @return {@code true}, if any of the matchers match the provided string
     */
    @Nullable
    public static WildcardMatcher anyMatch(List<WildcardMatcher> matchers, String s) {
        return anyMatch(matchers, s, null);
    }

    /**
     * Returns {@code true}, if any of the matchers match the provided partitioned string.
     *
     * @param matchers   the matchers which should be used to match the provided string
     * @param firstPart  The first part of the string to match against.
     * @param secondPart The second part of the string to match against.
     * @return {@code true}, if any of the matchers match the provided partitioned string
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

    abstract boolean matches(String s);

    abstract boolean matches(String firstPart, @Nullable String secondPart);

    private static class CompoundWildcardMatcher extends WildcardMatcher {
        private final String wildcardString;
        private final List<WildcardMatcher> wildcardMatchers;

        CompoundWildcardMatcher(String wildcardString, List<WildcardMatcher> wildcardMatchers) {
            this.wildcardString = wildcardString;
            this.wildcardMatchers = wildcardMatchers;
        }

        @Override
        boolean matches(String s) {
            for (int i = 0; i < wildcardMatchers.size(); i++) {
                if (!wildcardMatchers.get(i).matches(s)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        boolean matches(String firstPart, @Nullable String secondPart) {
            for (int i = 0; i < wildcardMatchers.size(); i++) {
                if (!wildcardMatchers.get(i).matches(firstPart, secondPart)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return wildcardString;
        }
    }

    private static class SimpleWildcardMatcher extends WildcardMatcher {

        private final String matcher;
        private final String stringRepresentation;
        private final boolean startsWith;
        private final boolean endsWith;
        private final boolean ignoreCase;

        private SimpleWildcardMatcher(String matcher, String stringRepresentation, boolean startsWith, boolean endsWith, boolean ignoreCase) {
            this.matcher = matcher;
            this.stringRepresentation = stringRepresentation;
            this.startsWith = startsWith;
            this.endsWith = endsWith;
            this.ignoreCase = ignoreCase;
        }


        private static boolean containsIgnoreCase(String src, String what) {
            final int length = what.length();
            if (length == 0)
                return true; // Empty string is contained

            final char firstLo = Character.toLowerCase(what.charAt(0));
            final char firstUp = Character.toUpperCase(what.charAt(0));

            for (int i = src.length() - length; i >= 0; i--) {
                // Quick check before calling the more expensive regionMatches() method:
                final char ch = src.charAt(i);
                if (ch != firstLo && ch != firstUp)
                    continue;

                if (src.regionMatches(true, i, what, 0, length))
                    return true;
            }

            return false;
        }

        @Override
        public String toString() {
            return stringRepresentation;
        }

        /**
         * Checks if the given string matches the wildcard pattern.
         * <p>
         * It supports these pattern styles:
         * {@code foo*, *foo, *foo*}
         * </p>
         * <p>
         * To perform a case-insensitive match,
         * prepend {@code (?i)} to your pattern.
         * Example: {@code (?i)foo*} matches the string <code>FOOBAR</code>
         * </p>
         * <p>
         * It does NOT support wildcards in between like {@code f*o}
         * or single character wildcards like {@code f?o}
         * </p>
         *
         * @param s the String to match
         * @return whether the String matches the given pattern
         */
        @Override
        public boolean matches(String s) {
            if (startsWith && endsWith) {
                return contains(s, matcher);
            } else if (startsWith) {
                return startsWith(s, matcher);
            } else if (endsWith) {
                return endsWith(s, matcher);
            } else {
                return equals(s, matcher);
            }
        }

        /**
         * This is a different version of {@link #matches(String)} which has the same semantics as calling
         * {@code matcher.matches(firstPart + secondPart);}.
         * <p>
         * The difference is that this method does not allocate a new string unless necessary.
         * </p>
         *
         * @param firstPart  The first part of the string to match against.
         * @param secondPart The second part of the string to match against.
         * @return {@code true},
         * when the wildcard pattern matches the partitioned string,
         * {@code false} otherwise.
         */
        @Override
        public boolean matches(String firstPart, @Nullable String secondPart) {
            if (secondPart == null) {
                return matches(firstPart);
            }
            if (startsWith && endsWith) {
                return contains(firstPart, matcher) ||
                    contains(secondPart, matcher) ||
                    matches(firstPart.concat(secondPart));
            } else if (startsWith) {
                if (firstPart.length() >= matcher.length()) {
                    return startsWith(firstPart, matcher);
                } else {
                    return startsWith(matcher, firstPart) && matches(firstPart.concat(secondPart));
                }
            } else if (endsWith) {
                if (secondPart.length() >= matcher.length()) {
                    return endsWith(secondPart, matcher);
                } else {
                    return endsWith(matcher, secondPart) && matches(firstPart.concat(secondPart));
                }
            } else {
                return startsWith(matcher, firstPart) && endsWith(matcher, secondPart) && equals(firstPart.concat(secondPart), matcher);
            }
        }

        private boolean startsWith(String s, String matcher) {
            return s.regionMatches(ignoreCase, 0, matcher, 0, matcher.length());
        }

        private boolean endsWith(String s, String matcher) {
            return s.regionMatches(ignoreCase, s.length() - matcher.length(), matcher, 0, matcher.length());
        }

        private boolean equals(String concat, String matcher) {
            return ignoreCase ? matcher.equalsIgnoreCase(concat) : matcher.equals(concat);
        }

        private boolean contains(String s, String matcher) {
            return ignoreCase ? containsIgnoreCase(s, matcher) : s.contains(matcher);
        }
    }
}
