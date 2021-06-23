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
package co.elastic.apm.agent.jmx;

import org.junit.jupiter.api.Test;

import javax.management.MalformedObjectNameException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JmxMetricValueConverterTest {

    @Test
    void testFromString() {
        JmxMetric expected = JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count]");
        assertThat(JmxMetric.valueOf(expected.toString())).isEqualTo(expected);
        JmxMetric expected2 = JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount]");
        assertThat(JmxMetric.valueOf(expected2.toString())).isEqualTo(expected2);
    }

    @Test
    void testInvalidObjectName() {
        assertThatThrownBy(() -> JmxMetric.valueOf("object_name[foo] attribute[foo]"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid syntax for object_name[foo]")
            .hasCauseInstanceOf(MalformedObjectNameException.class);
    }

    @Test
    void testInvalidAttribute() {
        assertThatThrownBy(() -> JmxMetric.valueOf("object_name[foo:bar=baz] attribute[foo:bar:baz]"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid syntax for attribute[foo:bar:baz]")
            .hasCauseInstanceOf(MalformedObjectNameException.class);
    }

    @Test
    void testMissingAttributes() {
        assertThatThrownBy(() -> JmxMetric.valueOf("attribute[CollectionCount:metric_name=collection_count]"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("object_name");
        assertThatThrownBy(() -> JmxMetric.valueOf("object_name[java.lang:type=GarbageCollector,name=*]"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("attribute");
    }

    @Test
    void testUnknownAttributes() {
        assertThatThrownBy(() -> JmxMetric.valueOf("object_name[foo:bar=baz] attribute[qux:quux=corge]"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown properties: [quux]");
        assertThatThrownBy(() -> JmxMetric.valueOf("object_name[foo:bar=baz] attribute[foo] foo[bar]"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown keys: [foo]");
    }
}
