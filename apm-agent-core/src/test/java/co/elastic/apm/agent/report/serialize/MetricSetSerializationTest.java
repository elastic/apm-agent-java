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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.metrics.MetricSet;
import co.elastic.apm.agent.report.serialize.MetricRegistrySerializer;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class MetricSetSerializationTest {

    private JsonWriter jw = new DslJson<>().newWriter();
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerialization() throws IOException {
        final MetricSet metricSet = new MetricSet(Collections.singletonMap("foo.bar", "baz"));
        metricSet.add("foo.bar", () -> 42);
        metricSet.add("bar.baz", () -> 42);
        MetricRegistrySerializer.serializeMetricSet(metricSet, System.currentTimeMillis() * 1000, new StringBuilder(), jw);
        final String metricSetAsString = jw.toString();
        System.out.println(metricSetAsString);
        final JsonNode jsonNode = objectMapper.readTree(metricSetAsString);
        assertThat(jsonNode.get("metricset").get("samples").get("foo.bar").get("value").doubleValue()).isEqualTo(42);
    }
}
