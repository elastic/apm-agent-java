/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.attach;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

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
        assertThat(RemoteAttacher.Arguments.parse("-p", "42").getPid()).isEqualTo("42");
        assertThat(RemoteAttacher.Arguments.parse("--pid", "42").getPid()).isEqualTo("42");
        assertThat(RemoteAttacher.Arguments.parse("--args", "foo=bar").getArgs()).isEqualTo("foo=bar");
        assertThat(RemoteAttacher.Arguments.parse("-a", "foo=bar").getArgs()).isEqualTo("foo=bar");
        assertThat(RemoteAttacher.Arguments.parse("--args-provider", "foo").getArgsProvider()).isEqualTo("foo");
        assertThat(RemoteAttacher.Arguments.parse("-A", "foo").getArgsProvider()).isEqualTo("foo");

        assertThat(RemoteAttacher.Arguments.parse("--exclude", "foo", "bar", "baz").getExcludes()).isEqualTo(Arrays.asList("foo", "bar", "baz"));
        assertThat(RemoteAttacher.Arguments.parse("--args", "foo", "-e", "foo", "bar", "baz").getExcludes()).isEqualTo(Arrays.asList("foo", "bar", "baz"));
        assertThat(RemoteAttacher.Arguments.parse("--include", "foo", "bar", "baz").getIncludes()).isEqualTo(Arrays.asList("foo", "bar", "baz"));
        assertThat(RemoteAttacher.Arguments.parse("-i", "foo", "bar", "baz", "--args", "42").getIncludes()).isEqualTo(Arrays.asList("foo", "bar", "baz"));
        assertThatThrownBy(() -> RemoteAttacher.Arguments.parse("--args", "foo=bar", "--args-provider", "foo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RemoteAttacher.Arguments.parse("--pid", "42", "--exclude", "foo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RemoteAttacher.Arguments.parse("--pid", "42", "--continuous")).isInstanceOf(IllegalArgumentException.class);

        assertThat(RemoteAttacher.Arguments.parse("-ca", "foo=bar").getArgs()).isEqualTo("foo=bar");
        assertThat(RemoteAttacher.Arguments.parse("-ca", "foo=bar").isContinuous()).isTrue();

        assertThatThrownBy(() -> RemoteAttacher.Arguments.parse("-lax")).isInstanceOf(IllegalArgumentException.class).hasMessage("Illegal argument: -x");
    }
}
