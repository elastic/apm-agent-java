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
package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ForLoopReplaceableByForEach")
public abstract class WildcardMatcherUtil {

    private static final String CASE_INSENSITIVE_PREFIX = "(?i)";
    private static final String CASE_SENSITIVE_PREFIX = "(?-i)";
    private static final String WILDCARD = "*";

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

    public static boolean isAnyMatch(List<? extends WildcardMatcher> matchers, @Nullable CharSequence s) {
        return anyMatch(matchers, s) != null;
    }

    public static boolean isNoneMatch(List<? extends WildcardMatcher> matchers, @Nullable CharSequence s) {
        return !isAnyMatch(matchers, s);
    }

    @Nullable
    public static WildcardMatcher anyMatch(List<? extends WildcardMatcher> matchers, @Nullable CharSequence s) {
        if (s == null || matchers.isEmpty()) {
            return null;
        }
        return anyMatch(matchers, s, null);
    }

    @Nullable
    public static WildcardMatcher anyMatch(List<? extends WildcardMatcher> matchers, CharSequence firstPart, @Nullable CharSequence secondPart) {
        for (int i = 0; i < matchers.size(); i++) {
            if (matchers.get(i).matches(firstPart, secondPart)) {
                return matchers.get(i);
            }
        }
        return null;
    }

    public static int indexOfIgnoreCase(final CharSequence haystack1, final CharSequence haystack2, final String needle, final boolean ignoreCase, final int start, final int end) {
        if (start < 0) {
            return -1;
        }
        int totalHaystackLength = haystack1.length() + haystack2.length();
        if (needle.isEmpty() || totalHaystackLength == 0) {
            // Fallback to legacy behavior.
            return indexOf(haystack1, needle);
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

    private static int indexOf(CharSequence input, String s) {
        if (input instanceof StringBuilder) {
            return ((StringBuilder) input).indexOf(s);
        }
        return input.toString().indexOf(s);
    }

    static char charAt(int i, CharSequence firstPart, CharSequence secondPart, int firstPartLength) {
        return i < firstPartLength ? firstPart.charAt(i) : secondPart.charAt(i - firstPartLength);
    }

    static class CompoundWildcardMatcher implements WildcardMatcher {
        private final String wildcardString;
        private final String matcher;
        private final List<SimpleWildcardMatcher> wildcardMatchers;

        CompoundWildcardMatcher(String wildcardString, String matcher, List<SimpleWildcardMatcher> wildcardMatchers) {
            this.wildcardString = wildcardString;
            this.matcher = matcher;
            this.wildcardMatchers = wildcardMatchers;
        }

        @Override
        public boolean matches(CharSequence s) {
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
        public boolean matches(CharSequence firstPart, @Nullable CharSequence secondPart) {
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
    static class SimpleWildcardMatcher implements WildcardMatcher {

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
        public boolean matches(CharSequence s) {
            return indexOf(s, 0) != -1;
        }

        @Override
        public boolean matches(CharSequence firstPart, @Nullable CharSequence secondPart) {
            return indexOf(firstPart, secondPart, 0) != -1;
        }

        int indexOf(final CharSequence s, final int offset) {
            return indexOf(s, "", offset);
        }

        int indexOf(CharSequence firstPart, @Nullable CharSequence secondPart, int offset) {
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
