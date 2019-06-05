/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabelsTest {

    @Test
    void testCharSequenceHash() {
        assertThat(Labels.Mutable.hash("foo")).isEqualTo(Labels.Mutable.hash(new StringBuilder("foo")));
    }

    @Test
    void testEqualsHashCode() {
        assertEqualsHashCode(
            Labels.Mutable.of("foo", "bar"),
            Labels.Mutable.of("foo", "bar"));
        assertEqualsHashCode(
            Labels.Mutable.of().transactionName("foo"),
            Labels.Mutable.of().transactionName("foo"));
        assertEqualsHashCode(
            Labels.Mutable.of().transactionName("foo"),
            Labels.Mutable.of().transactionName(new StringBuilder("foo")));
        assertEqualsHashCode(
            Labels.Mutable.of("foo", "bar"),
            Labels.Mutable.of("foo", new StringBuilder("bar")));
        assertEqualsHashCode(
            Labels.Mutable.of("foo", new StringBuilder("bar")).add("baz", "qux"),
            Labels.Mutable.of("foo", "bar").add("baz", new StringBuilder("qux")));
        assertEqualsHashCode(
            Labels.Mutable.of("foo", "bar"),
            Labels.Mutable.of("foo", new StringBuilder("bar")).immutableCopy());
    }

    @Test
    void testNotEquals() {
        assertNotEqual(
            Labels.Mutable.of("foo", "bar"),
            Labels.Mutable.of("bar", "foo"));
        assertNotEqual(
            Labels.Mutable.of("foo", "bar").add("baz", "qux"),
            Labels.Mutable.of("baz", "qux").add("foo", "bar"));
        assertNotEqual(
            Labels.Mutable.of("foo", "bar").add("baz", "qux"),
            Labels.Mutable.of("baz", "qux").add("foo", "bar"));
    }

    @Test
    void testRecycle() {
        final Labels.Mutable resetLabels = Labels.Mutable.of("foo", "bar").transactionName(new StringBuilder("baz"));
        final Labels immutableLabels = resetLabels.immutableCopy();
        resetLabels.resetState();
        assertEqualsHashCode(
            immutableLabels,
            Labels.Mutable.of("foo", "bar").transactionName("baz"));
        assertNotEqual(resetLabels, immutableLabels);
        assertEqualsHashCode(resetLabels, Labels.EMPTY);
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
