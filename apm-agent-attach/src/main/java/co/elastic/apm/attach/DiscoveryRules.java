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
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

class DiscoveryRules {

    private final List<Condition> conditions = new ArrayList<>();

    public void include(Condition condition) {
        conditions.add(condition);
    }

    public void exclude(Condition condition) {
        conditions.add(new NegatingCondition(condition));
    }

    public void includeAll() {
        include(ConstantCondition.ALL);
    }

    public void includeCommandsMatching(String pattern) {
        include(new CmdCondition(Pattern.compile(pattern)));
    }

    public void excludeCommandsMatching(String pattern) {
        exclude(new CmdCondition(Pattern.compile(pattern)));
    }

    public void includePid(String pid) {
        include(new PidCondition(pid));
    }

    public void excludePid(String pid) {
        exclude(new PidCondition(pid));
    }

    public void includeUser(String user) {
        include(new UserCondition(user));
    }

    public void excludeUser(String user) {
        exclude(new UserCondition(user));
    }

    public boolean isAnyMatch(JvmInfo vm, UserRegistry userRegistry) {
        return anyMatch(vm, userRegistry) != null;
    }

    public Condition anyMatch(JvmInfo vm, UserRegistry userRegistry) {
        for (Condition condition : conditions) {
            if (condition.matches(vm, userRegistry)) {
                return condition;
            }
        }
        return null;
    }

    public Collection<Condition> getConditions() {
        return new ArrayList<>(conditions);
    }

    private enum ConstantCondition implements Condition {

        ALL(true),
        NONE(false);

        private final boolean matches;

        ConstantCondition(boolean matches) {
            this.matches = matches;
        }

        @Override
        public boolean matches(JvmInfo vm, UserRegistry userRegistry) {
            return matches;
        }
    }

    interface Condition {
        boolean matches(JvmInfo vm, UserRegistry userRegistry);
    }

    private static class CmdCondition implements Condition {
        private final Pattern matcher;

        private CmdCondition(Pattern matcher) {
            this.matcher = matcher;
        }

        public boolean matches(JvmInfo vm, UserRegistry userRegistry) {
            try {
                String command = vm.getCmd(userRegistry);
                return matcher.matcher(command).find();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return "cmd(" + matcher + ")";
        }
    }

    private static class PidCondition implements Condition {

        private final String pid;

        private PidCondition(String pid) {
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

    private static class UserCondition implements Condition {

        private final String user;

        private UserCondition(String user) {
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

    public static class NegatingCondition implements Condition {

        private final Condition condition;

        public NegatingCondition(Condition condition) {
            this.condition = condition;
        }

        @Override
        public boolean matches(JvmInfo vm, UserRegistry userRegistry) {
            return !condition.matches(vm, userRegistry);
        }

        @Override
        public String toString() {
            return "not(" + condition + ')';
        }
    }
}
