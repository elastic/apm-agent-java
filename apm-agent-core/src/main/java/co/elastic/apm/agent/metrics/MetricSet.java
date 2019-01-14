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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A metric set is a collection of metrics which have the same tags.
 * <p>
 * A metric set corresponds to one document per
 * {@link co.elastic.apm.agent.report.ReporterConfiguration#metricsInterval metrics_interval} in Elasticsearch.
 * An alternative would be to have one document per metric but having one document for all metrics with the same tags saves disk space.
 * </p>
 * Example of some serialized metric sets:
 * <pre>
 * {"metricset":{"timestamp":1545047730692000,"samples":{"jvm.gc.alloc":{"value":24089200.0}}}}
 * {"metricset":{"timestamp":1545047730692000,"tags":{"name":"G1 Young Generation"},"samples":{"jvm.gc.time":{"value":0.0},"jvm.gc.count":{"value":0.0}}}}
 * {"metricset":{"timestamp":1545047730692000,"tags":{"name":"G1 Old Generation"},  "samples":{"jvm.gc.time":{"value":0.0},"jvm.gc.count":{"value":0.0}}}}
 * </pre>
 */
public class MetricSet {
    private final Map<String, String> tags;
    private final ConcurrentMap<String, DoubleSupplier> samples = new ConcurrentHashMap<>();

    public MetricSet(Map<String, String> tags) {
        this.tags = tags;
    }

    public void add(String name, DoubleSupplier metric) {
        samples.putIfAbsent(name, metric);
    }

    DoubleSupplier get(String name) {
        return samples.get(name);
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public Map<String, DoubleSupplier> getSamples() {
        return samples;
    }
}
