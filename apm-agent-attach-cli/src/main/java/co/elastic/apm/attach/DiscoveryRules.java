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
package co.elastic.apm.attach;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

class DiscoveryRules {

    private final List<DiscoveryRule> discoveryRules = new ArrayList<>();

    private void include(Matcher matcher) {
        discoveryRules.add(DiscoveryRule.include(matcher));
    }

    private void exclude(Matcher matcher) {
        discoveryRules.add(DiscoveryRule.exclude(matcher));
    }

    public void includeAll() {
        include(ConstantMatcher.ALL);
    }

    public void includeMain(String pattern) {
        include(new MainClassMatcher(Pattern.compile(pattern)));
    }

    public void excludeMain(String pattern) {
        exclude(new MainClassMatcher(Pattern.compile(pattern)));
    }

    public void includeVmArgs(String pattern) {
        include(new VmArgsMatcher(Pattern.compile(pattern)));
    }

    public void excludeVmArgs(String pattern) {
        exclude(new VmArgsMatcher(Pattern.compile(pattern)));
    }

    public void includePid(String pid) {
        include(new PidMatcher(pid));
    }

    public void excludePid(String pid) {
        exclude(new PidMatcher(pid));
    }

    public void includeUser(String user) {
        include(new UserMatcher(user));
    }

    public void excludeUser(String user) {
        exclude(new UserMatcher(user));
    }

    public boolean isMatching(JvmInfo vm, UserRegistry userRegistry) {
        DiscoveryRule firstMatch = firstMatch(vm, userRegistry);
        return firstMatch != null && firstMatch.getMatchingType() == MatcherType.INCLUDE;
    }

    public DiscoveryRule firstMatch(JvmInfo vm, UserRegistry userRegistry) {
        for (DiscoveryRule discoveryRule : discoveryRules) {
            if (discoveryRule.matches(vm, userRegistry)) {
                return discoveryRule;
            }
        }
        return null;
    }

    public Collection<DiscoveryRule> getMatcherRules() {
        return new ArrayList<>(discoveryRules);
    }

    public Collection<DiscoveryRule> getIncludeRules() {
        ArrayList<DiscoveryRule> includeRules = new ArrayList<>(this.discoveryRules);
        for (Iterator<DiscoveryRule> iterator = includeRules.iterator(); iterator.hasNext(); ) {
            if (iterator.next().matcherType != MatcherType.INCLUDE) {
                iterator.remove();
            }
        }
        return includeRules;
    }

    public Collection<DiscoveryRule> getExcludeRules() {
        ArrayList<DiscoveryRule> excludeRules = new ArrayList<>(this.discoveryRules);
        for (Iterator<DiscoveryRule> iterator = excludeRules.iterator(); iterator.hasNext(); ) {
            if (iterator.next().matcherType != MatcherType.EXCLUDE) {
                iterator.remove();
            }
        }
        return excludeRules;
    }

    public static class DiscoveryRule implements Matcher {

        private final Matcher matcher;

        private final MatcherType matcherType;
        private DiscoveryRule(Matcher matcher, MatcherType matcherType) {
            this.matcher = matcher;
            this.matcherType = matcherType;
        }

        public static DiscoveryRule include(Matcher matcher) {
            return new DiscoveryRule(matcher, MatcherType.INCLUDE);
        }

        public static DiscoveryRule exclude(Matcher matcher) {
            return new DiscoveryRule(matcher, MatcherType.EXCLUDE);
        }

        public MatcherType getMatchingType() {
            return matcherType;
        }

        @Override
        public boolean matches(JvmInfo vm, UserRegistry userRegistry) {
            return matcher.matches(vm, userRegistry);
        }

        @Override
        public String toString() {
            return matcherType == MatcherType.INCLUDE ? "include(" + matcher + ')' : "exclude(" + matcher + ')';
        }

    }

    public enum MatcherType {
        INCLUDE, EXCLUDE;
    }

    interface Matcher {
        boolean matches(JvmInfo vm, UserRegistry userRegistry);
    }

    private enum ConstantMatcher implements Matcher {

        ALL(true),
        NONE(false);

        private final boolean matches;

        ConstantMatcher(boolean matches) {
            this.matches = matches;
        }

        @Override
        public boolean matches(JvmInfo vm, UserRegistry userRegistry) {
            return matches;
        }

    }

    private static class MainClassMatcher implements Matcher {
        private final Pattern matcher;

        private MainClassMatcher(Pattern matcher) {
            this.matcher = matcher;
        }

        public boolean matches(JvmInfo vm, UserRegistry userRegistry) {
            try {
                String mainClass = vm.getMainClass();
                return (mainClass != null && matcher.matcher(mainClass).find());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return "main(" + matcher + ")";
        }
    }

    private static class VmArgsMatcher implements Matcher {
        private final Pattern matcher;

        private VmArgsMatcher(Pattern matcher) {
            this.matcher = matcher;
        }

        public boolean matches(JvmInfo vm, UserRegistry userRegistry) {
            try {
                String vmArgs = vm.getVmArgs();
                return vmArgs != null && matcher.matcher(vmArgs).find();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return "vmArgs(" + matcher + ")";
        }
    }

    private static class PidMatcher implements Matcher {

        private final String pid;

        private PidMatcher(String pid) {
            this.pid = Objects.requireNonNull(pid);
        }

        @Override
        public boolean matches(JvmInfo vm, UserRegistry userRegistry) {
            return this.pid.equals(vm.getPid());
        }

        @Override
        public String toString() {
            return "pid(" + pid + ")";
        }
    }

    private static class UserMatcher implements Matcher {

        private final String user;

        private UserMatcher(String user) {
            this.user = user;
        }

        @Override
        public boolean matches(JvmInfo vm, UserRegistry userRegistry) {
            return this.user.equals(vm.getUserName());
        }

        @Override
        public String toString() {
            return "user(" + user + ")";
        }
    }

}
