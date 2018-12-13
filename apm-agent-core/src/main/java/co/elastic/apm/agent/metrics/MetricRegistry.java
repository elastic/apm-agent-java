/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

import com.dslplatform.json.JsonWriter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetricRegistry {

    private static final byte NEW_LINE = '\n';
    private final Map<Map<String, String>, MetricSet> metricSets = new ConcurrentHashMap<>();

    public void addUnlessNan(String name, Map<String, String> tags, DoubleSupplier metric) {
        if (!Double.isNaN(metric.get())) {
            add(name, tags, metric);
        }
    }

    public void add(String name, Map<String, String> tags, DoubleSupplier metric) {
        MetricSet metricSet = metricSets.get(tags);
        if (metricSet == null) {
            metricSets.putIfAbsent(tags, new MetricSet(tags));
            metricSet = metricSets.get(tags);
        }
        metricSet.add(name, metric);
    }

    public void serialize(JsonWriter jw, StringBuilder replaceBuilder) {
        final long timestamp = System.currentTimeMillis() * 1000;
        for (MetricSet metricSet : metricSets.values()) {
            metricSet.serialize(timestamp, replaceBuilder, jw);
            jw.writeByte(NEW_LINE);
        }
    }

    public double get(String name, Map<String, String> tags) {
        final MetricSet metricSet = metricSets.get(tags);
        if (metricSet != null) {
            return metricSet.get(name).get();
        }
        return Double.NaN;
    }
}
