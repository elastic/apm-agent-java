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
package co.elastic.apm.attach;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentAttacherTest {

    @Test
    void testArgumentParsing() {
        assertThat(AgentAttacher.Arguments.parse().isHelp()).isTrue();
        assertThat(AgentAttacher.Arguments.parse("-h").isHelp()).isTrue();
        assertThat(AgentAttacher.Arguments.parse("--help").isHelp()).isTrue();
        assertThat(AgentAttacher.Arguments.parse("-l").isHelp()).isFalse();
        assertThat(AgentAttacher.Arguments.parse("-l").isList()).isTrue();
        assertThat(AgentAttacher.Arguments.parse("--list").isList()).isTrue();
        assertThat(AgentAttacher.Arguments.parse("--list-vmargs").isListVmArgs()).isTrue();
        assertThat(AgentAttacher.Arguments.parse("-c").isContinuous()).isTrue();
        assertThat(AgentAttacher.Arguments.parse("--continuous").isContinuous()).isTrue();
        assertThat(AgentAttacher.Arguments.parse("--include-pid", "42").getDiscoveryRules().getIncludeRules()).hasSize(1);
        assertThat(AgentAttacher.Arguments.parse("--config", "foo=bar", "baz=qux").getConfig()).containsEntry("foo", "bar").containsEntry("baz", "qux");
        assertThat(AgentAttacher.Arguments.parse("-C", "foo=bar", "-C", "baz=qux").getConfig()).containsEntry("foo", "bar").containsEntry("baz", "qux");
        assertThat(AgentAttacher.Arguments.parse("--args-provider", "foo").getArgsProvider()).isEqualTo("foo");
        assertThat(AgentAttacher.Arguments.parse("-A", "foo").getArgsProvider()).isEqualTo("foo");

        assertThat(AgentAttacher.Arguments.parse("--include-all").getDiscoveryRules().getMatcherRules()).hasSize(1);
        assertThat(AgentAttacher.Arguments.parse("--exclude-user", "root").getDiscoveryRules().getExcludeRules()).hasSize(1);
        assertThat(AgentAttacher.Arguments.parse("--include-user", "root").getDiscoveryRules().getIncludeRules()).hasSize(1);
        assertThat(AgentAttacher.Arguments.parse("--exclude-vmargs", "foo").getDiscoveryRules().getExcludeRules()).hasSize(1);
        assertThat(AgentAttacher.Arguments.parse("--include-vmargs", "foo").getDiscoveryRules().getIncludeRules()).hasSize(1);
        assertThat(AgentAttacher.Arguments.parse("--exclude-main", "foo", "bar", "baz").getDiscoveryRules().getExcludeRules()).hasSize(3);
        assertThat(AgentAttacher.Arguments.parse("--config", "foo=bar", "--exclude-main", "foo", "bar", "baz").getDiscoveryRules().getExcludeRules()).hasSize(3);
        assertThat(AgentAttacher.Arguments.parse("--include-main", "foo", "bar", "baz").getDiscoveryRules().getIncludeRules()).hasSize(3);
        assertThat(AgentAttacher.Arguments.parse("--include-main", "foo", "bar", "baz", "--config", "foo=bar").getDiscoveryRules().getIncludeRules()).hasSize(3);

        assertThat(AgentAttacher.Arguments.parse("--log-level", "debug").getLogLevel()).isEqualTo(Level.DEBUG);
        assertThat(AgentAttacher.Arguments.parse("--log-file", "foo.log").getLogFile()).isEqualTo("foo.log");

        assertThatThrownBy(() -> AgentAttacher.Arguments.parse("--agent-jar", "foo.jar"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("foo.jar does not exist");

        assertThatThrownBy(() -> AgentAttacher.Arguments.parse("--config", "foo=bar", "--args-provider", "foo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentAttacher.Arguments.parse("--include-main", "[")).isInstanceOf(IllegalArgumentException.class);

        assertThat(AgentAttacher.Arguments.parse("-cC", "foo=bar").getConfig()).containsEntry("foo", "bar");
        assertThat(AgentAttacher.Arguments.parse("-cC", "foo=bar").isContinuous()).isTrue();

        assertThatThrownBy(() -> AgentAttacher.Arguments.parse("-lcx")).isInstanceOf(IllegalArgumentException.class).hasMessage("Illegal argument: -x");
    }
}
