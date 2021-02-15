/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
        assertThat(AgentAttacher.Arguments.parse("-c").isContinuous()).isTrue();
        assertThat(AgentAttacher.Arguments.parse("--continuous").isContinuous()).isTrue();
        assertThat(AgentAttacher.Arguments.parse("-p", "42").getDiscoveryRules().getConditions()).hasSize(1);
        assertThat(AgentAttacher.Arguments.parse("--pid", "42").getDiscoveryRules().getConditions()).hasSize(1);
        assertThat(AgentAttacher.Arguments.parse("--config", "foo=bar", "baz=qux").getConfig()).containsEntry("foo", "bar").containsEntry("baz", "qux");
        assertThat(AgentAttacher.Arguments.parse("-C", "foo=bar", "-C", "baz=qux").getConfig()).containsEntry("foo", "bar").containsEntry("baz", "qux");
        assertThat(AgentAttacher.Arguments.parse("--args-provider", "foo").getArgsProvider()).isEqualTo("foo");
        assertThat(AgentAttacher.Arguments.parse("-A", "foo").getArgsProvider()).isEqualTo("foo");

        assertThat(AgentAttacher.Arguments.parse("--include-all").getDiscoveryRules().getConditions()).hasSize(1);
        assertThat(AgentAttacher.Arguments.parse("--exclude-user", "root").getDiscoveryRules().getConditions()).hasSize(1);
        assertThat(AgentAttacher.Arguments.parse("--include-user", "root").getDiscoveryRules().getConditions()).hasSize(1);
        assertThat(AgentAttacher.Arguments.parse("--exclude", "foo", "bar", "baz").getDiscoveryRules().getConditions()).hasSize(3);
        assertThat(AgentAttacher.Arguments.parse("--exclude-cmd", "foo", "bar", "baz").getDiscoveryRules().getConditions()).hasSize(3);
        assertThat(AgentAttacher.Arguments.parse("--config", "foo=bar", "-e", "foo", "bar", "baz").getDiscoveryRules().getConditions()).hasSize(3);
        assertThat(AgentAttacher.Arguments.parse("--include", "foo", "bar", "baz").getDiscoveryRules().getConditions()).hasSize(3);
        assertThat(AgentAttacher.Arguments.parse("--include-cmd", "foo", "bar", "baz").getDiscoveryRules().getConditions()).hasSize(3);
        assertThat(AgentAttacher.Arguments.parse("-i", "foo", "bar", "baz", "--config", "foo=bar").getDiscoveryRules().getConditions()).hasSize(3);
        assertThatThrownBy(() -> AgentAttacher.Arguments.parse("--config", "foo=bar", "--args-provider", "foo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentAttacher.Arguments.parse("--include-cmd", "[")).isInstanceOf(IllegalArgumentException.class);

        assertThat(AgentAttacher.Arguments.parse("-cC", "foo=bar").getConfig()).containsEntry("foo", "bar");
        assertThat(AgentAttacher.Arguments.parse("-cC", "foo=bar").isContinuous()).isTrue();

        assertThatThrownBy(() -> AgentAttacher.Arguments.parse("-lcx")).isInstanceOf(IllegalArgumentException.class).hasMessage("Illegal argument: -x");
    }
}
