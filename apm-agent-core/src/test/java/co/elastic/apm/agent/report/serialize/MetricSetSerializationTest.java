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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ReporterConfiguration;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MetricSetSerializationTest {

    private JsonWriter jw = new DslJson<>().newWriter();
    private ObjectMapper objectMapper = new ObjectMapper();
    private MetricRegistry registry = new MetricRegistry(mock(ReporterConfiguration.class));

    @Test
    void testSerializeGauges() throws IOException {
        final Labels.Mutable labels = Labels.Mutable.of("foo.bar", "baz");
        registry.add("foo.bar", labels, () -> 42);
        registry.add("bar.baz", labels, () -> 42);

        final JsonNode jsonNode = reportAsJson(labels);

        assertThat(jsonNode.get("metricset").get("samples").get("foo.bar").get("value").doubleValue()).isEqualTo(42);
    }

    @Test
    void testSerializeTimers() throws IOException {
        final Labels.Mutable labels = Labels.Mutable.of("foo.bar", "baz");

        registry.updateTimer("foo.bar", labels, 42);
        registry.updateTimer("bar.baz", labels, 42, 2);
        final JsonNode jsonNode = reportAsJson(labels);
        final JsonNode samples = jsonNode.get("metricset").get("samples");
        assertThat(samples.get("foo.bar.sum.us").get("value").doubleValue()).isEqualTo(42);
        assertThat(samples.get("foo.bar.count").get("value").doubleValue()).isEqualTo(1);
        assertThat(samples.get("bar.baz.sum.us").get("value").doubleValue()).isEqualTo(42);
        assertThat(samples.get("bar.baz.count").get("value").doubleValue()).isEqualTo(2);
    }

    @Test
    void testSerializeTimersWithTopLevelLabels() throws IOException {
        final Labels.Mutable labels = Labels.Mutable.of("foo", "bar")
            .transactionName("foo")
            .transactionType("bar")
            .spanType("baz")
            .spanSubType("qux");
        registry.updateTimer("foo.bar", labels, 42);

        final JsonNode jsonNode = reportAsJson(labels);

        final JsonNode metricset = jsonNode.get("metricset");
        assertThat(metricset.get("tags").get("foo").textValue()).isEqualTo("bar");
        assertThat(metricset.get("tags").get("transaction_name")).isNull();
        assertThat(metricset.get("tags").get("transaction.name")).isNull();
        assertThat(metricset.get("transaction").get("name").textValue()).isEqualTo("foo");
        assertThat(metricset.get("transaction").get("type").textValue()).isEqualTo("bar");
        assertThat(metricset.get("span").get("type").textValue()).isEqualTo("baz");
        assertThat(metricset.get("span").get("subtype").textValue()).isEqualTo("qux");
        assertThat(metricset.get("samples").get("foo.bar.sum.us").get("value").doubleValue()).isEqualTo(42);
    }

    @Test
    void testSerializeTimersReset() throws IOException {
        final Labels.Mutable labels = Labels.Mutable.of("foo.bar", "baz");
        registry.updateTimer("foo.bar", labels, 42);
        registry.updateTimer("bar.baz", labels, 42, 2);

        reportAsJson(labels);

        registry.updateTimer("foo.bar", labels, 42);
        final JsonNode samples = reportAsJson(labels).get("metricset").get("samples");

        assertThat(samples.get("foo.bar.sum.us").get("value").doubleValue()).isEqualTo(42);
        assertThat(samples.get("foo.bar.count").get("value").doubleValue()).isEqualTo(1);
    }

    @Test
    void testSerializeEmptyMetricSet() throws IOException {
        final Labels.Mutable labels = Labels.Mutable.of("foo.bar", "baz");
        registry.updateTimer("foo.bar", labels, 42);

        assertThat(reportAsJson(labels).get("metricset").get("samples")).isNotEmpty();

        assertThat(reportAsJson(labels).get("metricset").get("samples")).isEmpty();
    }

    @Test
    void testNonFiniteSerialization() throws IOException {
        registry.add("valid", Labels.EMPTY, () -> 4.0);
        registry.add("infinite", Labels.EMPTY, () -> Double.POSITIVE_INFINITY);
        registry.add("NaN", Labels.EMPTY, () -> Double.NaN);
        registry.add("negative.infinite", Labels.EMPTY, () -> Double.NEGATIVE_INFINITY);
        registry.add("also.valid", Labels.EMPTY, () -> 5.0);

        JsonNode samples = reportAsJson(Labels.EMPTY).get("metricset").get("samples");

        assertThat(samples.size()).isEqualTo(2);
        assertThat(samples.get("valid").get("value").doubleValue()).isEqualTo(4.0);
        assertThat(samples.get("also.valid").get("value").doubleValue()).isEqualTo(5.0);
    }

    @Test
    void testNonFiniteCornerCasesSerialization() throws IOException {
        registry.add("infinite", Labels.EMPTY, () -> Double.POSITIVE_INFINITY);
        registry.add("NaN", Labels.EMPTY, () -> Double.NaN);
        registry.add("negative.infinite", Labels.EMPTY, () -> Double.NEGATIVE_INFINITY);

        final JsonNode jsonNode = reportAsJson(Labels.EMPTY);

        assertThat(jsonNode.get("metricset").get("samples")).isEmpty();
    }

    @Test
    void serializeEmptyMetricSet() throws IOException {
        registry.updateTimer("foo", Labels.EMPTY, 0, 0);

        final JsonNode jsonNode = reportAsJson(Labels.EMPTY);

        assertThat(jsonNode.get("metricset").get("samples")).isEmpty();
    }

    @Test
    void testCounterReset() throws IOException {
        registry.incrementCounter("foo", Labels.EMPTY);

        JsonNode samples = reportAsJson(Labels.EMPTY).get("metricset").get("samples");
        assertThat(samples.size()).isEqualTo(1);
        assertThat(samples.get("foo").get("value").intValue()).isOne();

        assertThat(reportAsJson(Labels.EMPTY).get("metricset").get("samples")).hasSize(0);
    }

    @Test
    void testTimerReset() throws IOException {
        registry.updateTimer("foo", Labels.EMPTY, 1);

        JsonNode samples = reportAsJson(Labels.EMPTY).get("metricset").get("samples");
        assertThat(samples.size()).isEqualTo(2);
        assertThat(samples.get("foo.sum.us").get("value").intValue()).isOne();
        assertThat(samples.get("foo.count").get("value").intValue()).isOne();

        assertThat(reportAsJson(Labels.EMPTY).get("metricset").get("samples")).hasSize(0);
    }

    @Nonnull
    private JsonNode reportAsJson(Labels labels) throws IOException {
        registry.flipPhaseAndReport(metricSets -> MetricRegistrySerializer.serializeMetricSet(metricSets.get(labels), System.currentTimeMillis() * 1000, new StringBuilder(), jw));
        final String jsonString = jw.toString();
        System.out.println(jsonString);
        final JsonNode json = objectMapper.readTree(jsonString);
        jw.reset();
        return json;
    }
}
