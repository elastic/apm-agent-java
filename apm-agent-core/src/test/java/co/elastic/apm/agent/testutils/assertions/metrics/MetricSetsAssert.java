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
package co.elastic.apm.agent.testutils.assertions.metrics;

import co.elastic.apm.agent.testutils.assertions.BaseAssert;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MetricSetsAssert extends BaseAssert<MetricSetsAssert, List<MetricSetJson>> {

    static final ObjectMapper jsonMapper = new ObjectMapper();

    public MetricSetsAssert(Collection<byte[]> serializedMetricSets) {
        this(deserializeMetricSets(serializedMetricSets));
    }

    private static List<MetricSetJson> deserializeMetricSets(Collection<byte[]> serializedMetricSets) {
        return serializedMetricSets.stream()
            .map(bytes -> {
                try {
                    MetricSetJson metricset = jsonMapper.readValue(bytes, MetricSetEventJson.class).metricset;
                    metricset.json = new String(bytes).replace("\n", "");
                    return metricset;
                } catch (IOException e) {
                    throw new RuntimeException("Failed to deserialize metricset", e);
                }
            })
            .collect(Collectors.toList());
    }

    protected MetricSetsAssert(List<MetricSetJson> metricSets) {
        super(metricSets, MetricSetsAssert.class);
    }


    public MetricSetsAssert hasMetricsetWithLabelsSatisfying(String key, Object value, Consumer<MetricSetAssert> assertions) {
        return hasMetricsetWithLabelsSatisfying(Collections.singletonMap(key, value), assertions);
    }

    public MetricSetsAssert hasMetricsetWithLabelsSatisfying(String key1, Object value1, String key2, Object value2, Consumer<MetricSetAssert> assertions) {
        return hasMetricsetWithLabelsSatisfying(Map.of(key1, value1, key2, value2), assertions);
    }

    public MetricSetAssert first() {
        if (actual.isEmpty()) {
            failWithMessage("Expected at least one metricset but none was found");
        }
        return new MetricSetAssert(actual.get(0));
    }

    public MetricSetsAssert hasMetricsetWithLabelsSatisfying(Map<String, Object> labels, Consumer<MetricSetAssert> assertions) {
        assertions.accept(new MetricSetAssert(extractMetricset(labels)));
        return this;
    }

    public MetricSetsAssert hasMetricsetCount(int expected) {
        if (actual.size() != expected) {
            failWithMessage("Excpected to contain %d metricsets but actually was %d. Contained metricsets:\n%s", expected, actual.size(), getAllMetricsetsAsString());
        }
        return this;
    }

    private MetricSetJson extractMetricset(Map<String, Object> labels) {
        Optional<MetricSetJson> ms = actual.stream().filter(ms2 -> Objects.equals(ms2.tags, labels)).findFirst();
        if (ms.isEmpty()) {
            String allLabels = getAllLabels();
            failWithMessage("Expected metricset with labels %s to exist but was not found in %d metricsets. Found metricset labels are:\n%s", labels, actual.size(), allLabels);
        }
        return ms.get();
    }

    @NotNull
    private String getAllLabels() {
        return actual.stream()
            .map(ms2 -> ms2.tags)
            .map(Objects::toString)
            .collect(Collectors.joining("\n"));
    }


    @NotNull
    private String getAllMetricsetsAsString() {
        return actual.stream()
            .map(ms -> ms.json)
            .collect(Collectors.joining("\n"));
    }

}
