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

/**
 * This matcher is used in {@link co.elastic.apm.configuration.WebConfiguration#ignoreUrls}
 * to disable tracing for certain URLs.
 * <p>
 * The advantage of this class is that it does not instantiate objects when calling {@link #matches(String)}.
 * The syntax should be very familiar to any developer.
 * The only thing that might be surprising is that it does not support wildcards in between like <code>foo*bar</code>.
 * However, this provides support for the most common use cases -
 * ignoring URLs paths starting with a specific string like <code>/resources/*</code> or <code>/heartbeat/*</code>
 * and ignoring URLs by file ending like <code>*.js</code>.
 * It also allows to have a single configuration option which,
 * depending on the input string,
 * allows for prefix, postfix and infix matching.
 * This implementation is also very fast,
 * as it just resorts to {@link String#startsWith(String)} and {@link String#endsWith(String)}.
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
public class WildcardMatcher {

    private final String matcher;
    private final String stringRepresentation;
    private final boolean startsWith;
    private final boolean endsWith;

    private WildcardMatcher(String matcher, String stringRepresentation, boolean startsWith, boolean endsWith) {
        this.matcher = matcher;
        this.stringRepresentation = stringRepresentation;
        this.startsWith = startsWith;
        this.endsWith = endsWith;
    }

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
    public static WildcardMatcher valueOf(String wildcardString) {
        String matcher = wildcardString;
        boolean startsWith = false;
        boolean endsWith = false;
        if (matcher.startsWith("*")) {
            endsWith = true;
            matcher = matcher.substring(1, matcher.length());
        }
        if (matcher.endsWith("*")) {
            startsWith = true;
            matcher = matcher.substring(0, matcher.length() - 1);
        }
        return new WildcardMatcher(matcher, wildcardString, startsWith, endsWith);
    }

    @Override
    public String toString() {
        return stringRepresentation;
    }

    /**
     * Checks if the given string matches the wildcard pattern.
     * <p>
     * It supports these pattern styles:
     * <code>foo*, *foo, *foo*</code>
     * </p>
     * <p>
     * It does NOT support wildcards in between like <code>f*o</code>
     * </p>
     *
     * @param s the String to match
     * @return whether the String matches the given pattern
     */
    public boolean matches(String s) {
        boolean matches = true;
        if (startsWith) {
            matches = s.startsWith(matcher);
        }
        if (endsWith) {
            matches = matches && s.endsWith(matcher);
        }
        if (!startsWith && !endsWith) {
            return matcher.equals(s);
        }
        return matches;
    }

    /**
     * This is a different version of {@link #matches(String)} which has the same semantics as calling
     * <code>matcher.matches(firstPart + secondPart);</code>.
     * <p>
     * The difference is that this method does not allocate a new string unless necessary.
     * </p>
     *
     * @param firstPart  The first part of the string to match against.
     * @param secondPart The second part of the string to match against.
     * @return <code>true</code>,
     * when the wildcard pattern matches the partitioned string,
     * <code>false</code> otherwise.
     */
    public boolean matches(String firstPart, String secondPart) {
        boolean matches = true;
        if (startsWith) {
            if (firstPart.length() >= matcher.length()) {
                matches = firstPart.startsWith(matcher);
            } else {
                return matches(firstPart.concat(secondPart));
            }
        }
        if (endsWith) {
            if (secondPart.length() >= matcher.length()) {
                matches = matches && secondPart.endsWith(matcher);
            } else {
                return matches(firstPart.concat(secondPart));
            }
        }
        if (!startsWith && !endsWith) {
            return matcher.equals(firstPart.concat(secondPart));
        }
        return matches;
    }
}
