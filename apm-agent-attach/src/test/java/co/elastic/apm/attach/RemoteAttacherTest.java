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

class RemoteAttacherTest {

    @Test
    void testArgumentParsing() {
        assertThat(RemoteAttacher.Arguments.parse().isHelp()).isTrue();
        assertThat(RemoteAttacher.Arguments.parse("-h").isHelp()).isTrue();
        assertThat(RemoteAttacher.Arguments.parse("--help").isHelp()).isTrue();
        assertThat(RemoteAttacher.Arguments.parse("-l").isHelp()).isFalse();
        assertThat(RemoteAttacher.Arguments.parse("-l").isList()).isTrue();
        assertThat(RemoteAttacher.Arguments.parse("--list").isList()).isTrue();
        assertThat(RemoteAttacher.Arguments.parse("-c").isContinuous()).isTrue();
        assertThat(RemoteAttacher.Arguments.parse("--continuous").isContinuous()).isTrue();
        assertThat(RemoteAttacher.Arguments.parse("-p", "42").getDiscoveryRules().getConditions()).hasSize(1);
        assertThat(RemoteAttacher.Arguments.parse("--pid", "42").getDiscoveryRules().getConditions()).hasSize(1);
        assertThat(RemoteAttacher.Arguments.parse("--args", "foo=bar;baz=qux").getConfig()).containsEntry("foo", "bar").containsEntry("baz", "qux");
        assertThat(RemoteAttacher.Arguments.parse("-a", "foo=bar").getConfig()).containsEntry("foo", "bar");
        assertThat(RemoteAttacher.Arguments.parse("--config", "foo=bar", "baz=qux").getConfig()).containsEntry("foo", "bar").containsEntry("baz", "qux");
        assertThat(RemoteAttacher.Arguments.parse("-C", "foo=bar", "-C", "baz=qux").getConfig()).containsEntry("foo", "bar").containsEntry("baz", "qux");
        assertThat(RemoteAttacher.Arguments.parse("--args-provider", "foo").getArgsProvider()).isEqualTo("foo");
        assertThat(RemoteAttacher.Arguments.parse("-A", "foo").getArgsProvider()).isEqualTo("foo");

        assertThat(RemoteAttacher.Arguments.parse("--include-all").getDiscoveryRules().getConditions()).hasSize(1);
        assertThat(RemoteAttacher.Arguments.parse("--exclude-user", "root").getDiscoveryRules().getConditions()).hasSize(1);
        assertThat(RemoteAttacher.Arguments.parse("--include-user", "root").getDiscoveryRules().getConditions()).hasSize(1);
        assertThat(RemoteAttacher.Arguments.parse("--exclude", "foo", "bar", "baz").getDiscoveryRules().getConditions()).hasSize(3);
        assertThat(RemoteAttacher.Arguments.parse("--exclude-cmd", "foo", "bar", "baz").getDiscoveryRules().getConditions()).hasSize(3);
        assertThat(RemoteAttacher.Arguments.parse("--config", "foo=bar", "-e", "foo", "bar", "baz").getDiscoveryRules().getConditions()).hasSize(3);
        assertThat(RemoteAttacher.Arguments.parse("--include", "foo", "bar", "baz").getDiscoveryRules().getConditions()).hasSize(3);
        assertThat(RemoteAttacher.Arguments.parse("--include-cmd", "foo", "bar", "baz").getDiscoveryRules().getConditions()).hasSize(3);
        assertThat(RemoteAttacher.Arguments.parse("-i", "foo", "bar", "baz", "--config", "foo=bar").getDiscoveryRules().getConditions()).hasSize(3);
        assertThatThrownBy(() -> RemoteAttacher.Arguments.parse("--config", "foo=bar", "--args-provider", "foo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RemoteAttacher.Arguments.parse("--include-cmd", "[")).isInstanceOf(IllegalArgumentException.class);

        assertThat(RemoteAttacher.Arguments.parse("-cC", "foo=bar").getConfig()).containsEntry("foo", "bar");
        assertThat(RemoteAttacher.Arguments.parse("-cC", "foo=bar").isContinuous()).isTrue();

        assertThatThrownBy(() -> RemoteAttacher.Arguments.parse("-lcx")).isInstanceOf(IllegalArgumentException.class).hasMessage("Illegal argument: -x");
    }
}
