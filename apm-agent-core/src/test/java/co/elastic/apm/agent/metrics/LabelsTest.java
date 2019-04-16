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
package co.elastic.apm.agent.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabelsTest {

    @Test
    void testCharSequenceHash() {
        assertThat(Labels.hash("foo")).isEqualTo(Labels.hash(new StringBuilder("foo")));
    }

    @Test
    void testEqualsHashCode() {
        assertEqualsHashCode(
            Labels.of("foo", "bar"),
            Labels.of("foo", "bar"));
        assertEqualsHashCode(
            Labels.of().transactionName("foo"),
            Labels.of().transactionName("foo"));
        assertEqualsHashCode(
            Labels.of().transactionName("foo"),
            Labels.of().transactionName(new StringBuilder("foo")));
        assertEqualsHashCode(
            Labels.of("foo", "bar"),
            Labels.of("foo", new StringBuilder("bar")));
        assertEqualsHashCode(
            Labels.of("foo", new StringBuilder("bar")).add("baz", "qux"),
            Labels.of("foo", "bar").add("baz", new StringBuilder("qux")));
        assertEqualsHashCode(
            Labels.of("foo", "bar"),
            Labels.of("foo", new StringBuilder("bar")).immutableCopy());
    }

    @Test
    void testNotEquals() {
        assertNotEqual(
            Labels.of("foo", "bar"),
            Labels.of("bar", "foo"));
        assertNotEqual(
            Labels.of("foo", "bar").add("baz", "qux"),
            Labels.of("baz", "qux").add("foo", "bar"));
        assertNotEqual(
            Labels.of("foo", "bar").add("baz", "qux"),
            Labels.of("baz", "qux").add("foo", "bar"));
    }

    @Test
    void testImmutable() {
        assertThatThrownBy(() -> Labels.of("foo", "bar").immutableCopy().add("bar", "baz")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> Labels.of("foo", "bar").immutableCopy().transactionName("bar")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> Labels.of("foo", "bar").immutableCopy().transactionType("bar")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> Labels.of("foo", "bar").immutableCopy().spanType("bar")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testRecycle() {
        final Labels resetLabels = Labels.of("foo", "bar").transactionName(new StringBuilder("baz"));
        final Labels immutableLabels = resetLabels.immutableCopy();
        resetLabels.resetState();
        assertEqualsHashCode(
            immutableLabels,
            Labels.of("foo", "bar").transactionName("baz"));
        assertNotEqual(resetLabels, immutableLabels);
        assertEqualsHashCode(resetLabels, new Labels());
    }

    private void assertNotEqual(Labels l1, Labels l2) {
        assertThat(l1.hashCode()).isNotEqualTo(l2.hashCode());
        assertThat(l1).isNotEqualTo(l2);
    }

    private void assertEqualsHashCode(Labels l1, Labels l2) {
        assertThat(l1).hasSameHashCodeAs(l2);
        assertThat(l1).isEqualTo(l2);
    }
}
