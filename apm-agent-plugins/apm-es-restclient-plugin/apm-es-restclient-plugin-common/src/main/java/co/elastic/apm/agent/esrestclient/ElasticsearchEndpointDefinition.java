/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package co.elastic.apm.agent.esrestclient;

import co.elastic.apm.agent.tracer.Span;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ElasticsearchEndpointDefinition {

    private static final String OTEL_PATH_PARTS_ATTRIBUTE_PREFIX = "db.elasticsearch.path_parts.";

    private static final String UNDERSCORE_REPLACEMENT = "0";

    private final String endpointName;
    private final List<Route> routes;

    private final boolean isSearchEndpoint;

    public ElasticsearchEndpointDefinition(
        String endpointName, String[] routes, boolean isSearchEndpoint) {
        this.endpointName = endpointName;
        this.routes = new ArrayList<>();
        for (String route : routes) {
            this.routes.add(new Route(route));
        }
        this.isSearchEndpoint = isSearchEndpoint;
    }


    public String getEndpointName() {
        return endpointName;
    }

    public boolean isSearchEndpoint() {
        return isSearchEndpoint;
    }

    public void addPathPartAttributes(String urlPath, Span<?> spanToEnrich) {
        for (Route route : routes) {
            if (route.hasParameters()) {
                EndpointPattern pattern = route.getEndpointPattern();
                Matcher matcher = pattern.createMatcher(urlPath);
                if (matcher.find()) {
                    for (String groupName : pattern.getPatternGroupNames()) {
                        String value = matcher.group(groupName);
                        String attributeKey = pattern.getOtelPathPartAttributeName(groupName);
                        spanToEnrich.withOtelAttribute(attributeKey, value);
                    }
                    return;
                }
            }
        }
    }

    List<Route> getRoutes() {
        return routes;
    }

    static final class Route {
        private final String name;
        private final boolean hasParameters;

        private volatile EndpointPattern epPattern;

        public Route(String name) {
            this.name = name;
            this.hasParameters = name.contains("{") && name.contains("}");
        }

        String getName() {
            return name;
        }

        boolean hasParameters() {
            return hasParameters;
        }

        private EndpointPattern getEndpointPattern() {
            // Intentionally NOT synchronizing here to avoid synchronization overhead.
            // Main purpose here is to cache the pattern without the need for strict thread-safety.
            if (epPattern == null) {
                epPattern = new EndpointPattern(this);
            }

            return epPattern;
        }
    }

    static final class EndpointPattern {
        private static final Pattern PATH_PART_NAMES_PATTERN = Pattern.compile("\\{([^}]+)}");
        private final Pattern pattern;
        private final Map<String, String> pathPartNamesToOtelAttributes;

        /**
         * Creates, compiles and caches a regular expression pattern and retrieves a set of
         * pathPartNames (names of the URL path parameters) for this route.
         *
         * <p>The regex pattern is later being used to match against a URL path to retrieve the URL path
         * parameters for that route pattern using named regex capture groups.
         */
        private EndpointPattern(Route route) {
            pattern = buildRegexPattern(route.getName());

            if (route.hasParameters()) {
                pathPartNamesToOtelAttributes = new HashMap<>();
                Matcher matcher = PATH_PART_NAMES_PATTERN.matcher(route.getName());
                while (matcher.find()) {
                    String groupName = matcher.group(1);

                    if (groupName != null) {
                        String actualPatternGroupName = groupName.replace("_", UNDERSCORE_REPLACEMENT);
                        pathPartNamesToOtelAttributes.put(actualPatternGroupName, OTEL_PATH_PARTS_ATTRIBUTE_PREFIX + groupName);
                    }
                }
            } else {
                pathPartNamesToOtelAttributes = Collections.emptyMap();
            }
        }

        /**
         * Builds a regex pattern from the parameterized route pattern.
         */
        static Pattern buildRegexPattern(String routeStr) {
            StringBuilder regexStr = new StringBuilder();
            regexStr.append('^');
            int startIdx = routeStr.indexOf("{");
            while (startIdx >= 0) {
                regexStr.append(routeStr.substring(0, startIdx));

                int endIndex = routeStr.indexOf("}");
                if (endIndex <= startIdx + 1) {
                    break;
                }

                // Append named capture group.
                // If group name contains an underscore `_` it is being replaced with `0`,
                // because `_` is not allowed in capture group names.
                regexStr.append("(?<");
                regexStr.append(
                    routeStr.substring(startIdx + 1, endIndex).replace("_", UNDERSCORE_REPLACEMENT));
                regexStr.append(">[^/]+)");

                routeStr = routeStr.substring(endIndex + 1);
                startIdx = routeStr.indexOf("{");
            }

            regexStr.append(routeStr);
            regexStr.append('$');

            return Pattern.compile(regexStr.toString());
        }

        Matcher createMatcher(String urlPath) {
            return pattern.matcher(urlPath);
        }

        String getOtelPathPartAttributeName(String patternGroupName) {
            String attributeName = pathPartNamesToOtelAttributes.get(patternGroupName);
            if (attributeName == null) {
                throw new IllegalArgumentException(patternGroupName + " is not a group of this pattern!");
            }
            return attributeName;
        }

        Collection<String> getPatternGroupNames() {
            return pathPartNamesToOtelAttributes.keySet();
        }
    }
}
