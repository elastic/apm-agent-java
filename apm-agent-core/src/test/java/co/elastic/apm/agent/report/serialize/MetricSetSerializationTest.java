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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ReporterConfiguration;
import com.dslplatform.json.JsonWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MetricSetSerializationTest {

    private ObjectMapper objectMapper = new ObjectMapper();
    private MetricRegistry registry = new MetricRegistry(mock(ReporterConfiguration.class));
    private MetricRegistrySerializer metricRegistrySerializer = new MetricRegistrySerializer();

    @Test
    void testSerializeGauges() throws Exception {
        final Labels.Mutable labels = Labels.Mutable.of("foo.bar", "baz");
        registry.add("foo.bar", labels, () -> 42);
        registry.add("bar.baz", labels, () -> 42);

        final JsonNode jsonNode = reportAsJson();
        assertThat(jsonNode).isNotNull();
        assertThat(jsonNode.get("metricset").get("samples").get("foo.bar").get("value").doubleValue()).isEqualTo(42);
    }

    @Test
    void testSerializeTimers() throws Exception {
        final Labels.Mutable labels = Labels.Mutable.of("foo.bar", "baz");

        registry.updateTimer("foo.bar", labels, 42);
        registry.updateTimer("bar.baz", labels, 42, 2);
        final JsonNode jsonNode = reportAsJson();
        assertThat(jsonNode).isNotNull();
        final JsonNode samples = jsonNode.get("metricset").get("samples");
        assertThat(samples.get("foo.bar.sum.us").get("value").doubleValue()).isEqualTo(42);
        assertThat(samples.get("foo.bar.count").get("value").doubleValue()).isEqualTo(1);
        assertThat(samples.get("bar.baz.sum.us").get("value").doubleValue()).isEqualTo(42);
        assertThat(samples.get("bar.baz.count").get("value").doubleValue()).isEqualTo(2);
    }

    @Test
    void testSerializeTimersWithTopLevelLabels() throws Exception {
        final Labels.Mutable labels = Labels.Mutable.of("foo", "bar")
            .transactionName("foo")
            .transactionType("bar")
            .spanType("baz")
            .spanSubType("qux");
        registry.updateTimer("foo.bar", labels, 42);

        final JsonNode jsonNode = reportAsJson();
        assertThat(jsonNode).isNotNull();

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
    void testSerializeTimersReset() throws Exception {
        final Labels.Mutable labels = Labels.Mutable.of("foo.bar", "baz");
        registry.updateTimer("foo.bar", labels, 42);
        registry.updateTimer("bar.baz", labels, 42, 2);

        reportAsJson();

        registry.updateTimer("foo.bar", labels, 42);
        JsonNode jsonNode = reportAsJson();
        assertThat(jsonNode).isNotNull();
        final JsonNode samples = jsonNode.get("metricset").get("samples");

        assertThat(samples.get("foo.bar.sum.us").get("value").doubleValue()).isEqualTo(42);
        assertThat(samples.get("foo.bar.count").get("value").doubleValue()).isEqualTo(1);
    }

    @Test
    void testSerializeEmptyMetricSet() throws Exception {
        final Labels.Mutable labels = Labels.Mutable.of("foo.bar", "baz");
        registry.updateTimer("foo.bar", labels, 42);

        JsonNode jsonNode = reportAsJson();
        assertThat(jsonNode).isNotNull();
        assertThat(jsonNode.get("metricset").get("samples")).isNotEmpty();

        assertThat(reportAsJson()).isNull();
    }

    @Test
    void testNonFiniteSerialization() throws Exception {
        registry.add("valid", Labels.EMPTY, () -> 4.0);
        registry.add("infinite", Labels.EMPTY, () -> Double.POSITIVE_INFINITY);
        registry.add("NaN", Labels.EMPTY, () -> Double.NaN);
        registry.add("negative.infinite", Labels.EMPTY, () -> Double.NEGATIVE_INFINITY);
        registry.add("also.valid", Labels.EMPTY, () -> 5.0);

        JsonNode jsonNode = reportAsJson();
        assertThat(jsonNode).isNotNull();
        JsonNode samples = jsonNode.get("metricset").get("samples");

        assertThat(samples.size()).isEqualTo(2);
        assertThat(samples.get("valid").get("value").doubleValue()).isEqualTo(4.0);
        assertThat(samples.get("also.valid").get("value").doubleValue()).isEqualTo(5.0);
    }

    @Test
    void testNonFiniteCornerCasesSerialization() throws Exception {
        registry.add("infinite", Labels.EMPTY, () -> Double.POSITIVE_INFINITY);
        registry.add("NaN", Labels.EMPTY, () -> Double.NaN);
        registry.add("negative.infinite", Labels.EMPTY, () -> Double.NEGATIVE_INFINITY);
        assertThat(reportAsJson()).isNull();
    }

    @Test
    void serializeEmptyMetricSet() throws Exception {
        registry.updateTimer("foo", Labels.EMPTY, 0, 0);
        assertThat(reportAsJson()).isNull();
    }

    @Test
    void testCounterReset() throws Exception {
        registry.incrementCounter("foo", Labels.EMPTY);

        JsonNode jsonNode = reportAsJson();
        assertThat(jsonNode).isNotNull();

        JsonNode samples = jsonNode.get("metricset").get("samples");
        assertThat(samples.size()).isEqualTo(1);
        assertThat(samples.get("foo").get("value").intValue()).isOne();

        assertThat(reportAsJson()).isNull();
    }

    @Test
    void testTimerReset() throws Exception {
        registry.updateTimer("foo", Labels.EMPTY, 1);

        JsonNode jsonNode = reportAsJson();
        assertThat(jsonNode).isNotNull();
        JsonNode samples = jsonNode.get("metricset").get("samples");
        assertThat(samples.size()).isEqualTo(2);
        assertThat(samples.get("foo.sum.us").get("value").intValue()).isOne();
        assertThat(samples.get("foo.count").get("value").intValue()).isOne();

        assertThat(reportAsJson()).isNull();
    }

    @Test
    void testServiceName() throws Exception {
        registry.updateTimer("foo", Labels.Mutable.of().serviceName("bar"), 1);

        JsonNode jsonNode = reportAsJson();
        assertThat(jsonNode).isNotNull();
        JsonNode serviceName = jsonNode.get("metricset").get("service").get("name");
        assertThat(serviceName.asText()).isEqualTo("bar");
    }

    @Test
    void testServiceNameOverride() throws Exception {
        registry.updateTimer("foo", Labels.Mutable.of(), 1);

        JsonNode jsonNode = reportAsJson(singletonList("bar"));
        assertThat(jsonNode).isNotNull();
        JsonNode serviceName = jsonNode.get("metricset").get("service").get("name");
        assertThat(serviceName.asText()).isEqualTo("bar");
    }

    @Nullable
    private JsonNode reportAsJson() throws Exception {
        return  reportAsJson(emptyList());
    }

    @Nullable
    private JsonNode reportAsJson(List<String> serviceNames) throws Exception {
        final CompletableFuture<JsonWriter> jwFuture = new CompletableFuture<>();
        registry.flipPhaseAndReport(
            metricSets -> jwFuture.complete(metricRegistrySerializer.serialize(metricSets.values().iterator().next(), serviceNames))
        );
        JsonNode json = null;
        JsonWriter jw = jwFuture.getNow(null);
        if (jw != null) {
            final String jsonString = jw.toString();
            System.out.println(jsonString);
            json = objectMapper.readTree(jsonString);
        }
        return json;
    }
}
