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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.MetricSet;
import co.elastic.apm.agent.report.ReporterConfiguration;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MetricSetSerializationTest {

    private JsonWriter jw = new DslJson<>().newWriter();
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializeGauges() throws IOException {
        final MetricSet metricSet = new MetricSet(Labels.Mutable.of("foo.bar", "baz"));
        metricSet.add("foo.bar", () -> 42);
        metricSet.add("bar.baz", () -> 42);
        MetricRegistrySerializer.serializeMetricSet(metricSet, System.currentTimeMillis() * 1000, new StringBuilder(), jw);
        final String metricSetAsString = jw.toString();
        System.out.println(metricSetAsString);
        final JsonNode jsonNode = objectMapper.readTree(metricSetAsString);
        assertThat(jsonNode.get("metricset").get("samples").get("foo.bar").get("value").doubleValue()).isEqualTo(42);
    }

    @Test
    void testSerializeTimers() throws IOException {
        final MetricSet metricSet = new MetricSet(Labels.Mutable.of("foo.bar", "baz"));
        metricSet.timer("foo.bar").update(42);
        metricSet.timer("bar.baz").update(42, 2);
        MetricRegistrySerializer.serializeMetricSet(metricSet, System.currentTimeMillis() * 1000, new StringBuilder(), jw);
        final String metricSetAsString = jw.toString();
        System.out.println(metricSetAsString);
        final JsonNode jsonNode = objectMapper.readTree(metricSetAsString);
        final JsonNode samples = jsonNode.get("metricset").get("samples");
        assertThat(samples.get("foo.bar.sum").get("value").doubleValue()).isEqualTo(42 / 1000.0);
        assertThat(samples.get("foo.bar.count").get("value").doubleValue()).isEqualTo(1);
        assertThat(samples.get("bar.baz.sum").get("value").doubleValue()).isEqualTo(42 / 1000.0);
        assertThat(samples.get("bar.baz.count").get("value").doubleValue()).isEqualTo(2);
    }

    @Test
    void testSerializeTimersWithTopLevelLabels() throws IOException {
        final MetricSet metricSet = new MetricSet(Labels.Mutable.of("foo", "bar")
            .transactionName("foo")
            .transactionType("bar")
            .spanType("baz"));
        metricSet.timer("foo.bar").update(42);
        MetricRegistrySerializer.serializeMetricSet(metricSet, System.currentTimeMillis() * 1000, new StringBuilder(), jw);
        final String metricSetAsString = jw.toString();
        System.out.println(metricSetAsString);
        final JsonNode jsonNode = objectMapper.readTree(metricSetAsString);
        final JsonNode metricset = jsonNode.get("metricset");
        assertThat(metricset.get("tags").get("foo").textValue()).isEqualTo("bar");
        assertThat(metricset.get("tags").get("transaction_name")).isNull();
        assertThat(metricset.get("tags").get("transaction.name")).isNull();
        assertThat(metricset.get("transaction").get("name").textValue()).isEqualTo("foo");
        assertThat(metricset.get("transaction").get("type").textValue()).isEqualTo("bar");
        assertThat(metricset.get("span").get("type").textValue()).isEqualTo("baz");
        assertThat(metricset.get("samples").get("foo.bar.sum").get("value").doubleValue()).isEqualTo(42 / 1000.0);
    }

    @Test
    void testSerializeTimersReset() throws IOException {
        final MetricSet metricSet = new MetricSet(Labels.Mutable.of("foo.bar", "baz"));
        metricSet.timer("foo.bar").update(42);
        metricSet.timer("bar.baz").update(42, 2);
        MetricRegistrySerializer.serializeMetricSet(metricSet, System.currentTimeMillis() * 1000, new StringBuilder(), jw);
        jw.reset();
        metricSet.timer("foo.bar").update(42);
        MetricRegistrySerializer.serializeMetricSet(metricSet, System.currentTimeMillis() * 1000, new StringBuilder(), jw);
        final String metricSetAsString = jw.toString();
        System.out.println(metricSetAsString);
        final JsonNode jsonNode = objectMapper.readTree(metricSetAsString);
        final JsonNode samples = jsonNode.get("metricset").get("samples");
        assertThat(samples.get("foo.bar.sum").get("value").doubleValue()).isEqualTo(42 / 1000.0);
        assertThat(samples.get("foo.bar.count").get("value").doubleValue()).isEqualTo(1);
    }

    @Test
    void testSerializeEmptyMetricSet() {
        final MetricRegistry metricRegistry = new MetricRegistry(mock(ReporterConfiguration.class));
        metricRegistry.timer("foo.bar", Labels.Mutable.of("foo.bar", "baz")).update(42);
        MetricRegistrySerializer.serialize(metricRegistry, new StringBuilder(), jw);
        assertThat(jw.toString()).isNotEmpty();
        jw.reset();
        MetricRegistrySerializer.serialize(metricRegistry, new StringBuilder(), jw);
        assertThat(jw.toString()).isEmpty();
    }

    @Test
    void testNonFiniteSerialization() throws IOException {
        final MetricSet metricSet = new MetricSet(Labels.Immutable.empty());
        metricSet.add("valid", () -> 4.0);
        metricSet.add("infinite", () -> Double.POSITIVE_INFINITY);
        metricSet.add("NaN", () -> Double.NaN);
        metricSet.add("negative.infinite", () -> Double.NEGATIVE_INFINITY);
        metricSet.add("also.valid", () -> 5.0);
        MetricRegistrySerializer.serializeMetricSet(metricSet, System.currentTimeMillis() * 1000, new StringBuilder(), jw);
        final String metricSetAsString = jw.toString();
        System.out.println(metricSetAsString);
        final JsonNode jsonNode = objectMapper.readTree(metricSetAsString);
        JsonNode samples = jsonNode.get("metricset").get("samples");
        assertThat(samples.size()).isEqualTo(2);
        assertThat(samples.get("valid").get("value").doubleValue()).isEqualTo(4.0);
        assertThat(samples.get("also.valid").get("value").doubleValue()).isEqualTo(5.0);
    }

    @Test
    void testNonFiniteCornerCasesSerialization() throws IOException {
        final MetricSet metricSet = new MetricSet(Labels.Immutable.empty());
        MetricRegistrySerializer.serializeMetricSet(metricSet, System.currentTimeMillis() * 1000, new StringBuilder(), jw);
        String metricSetAsString = jw.toString();
        System.out.println(metricSetAsString);
        JsonNode jsonNode = objectMapper.readTree(metricSetAsString);
        JsonNode samples = jsonNode.get("metricset").get("samples");
        assertThat(samples.size()).isEqualTo(0);

        metricSet.add("infinite", () -> Double.POSITIVE_INFINITY);
        metricSet.add("NaN", () -> Double.NaN);
        metricSet.add("negative.infinite", () -> Double.NEGATIVE_INFINITY);
        MetricRegistrySerializer.serializeMetricSet(metricSet, System.currentTimeMillis() * 1000, new StringBuilder(), jw);
        metricSetAsString = jw.toString();
        jsonNode = objectMapper.readTree(metricSetAsString);
        samples = jsonNode.get("metricset").get("samples");
        assertThat(samples.size()).isEqualTo(0);
    }
}
