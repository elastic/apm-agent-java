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
package co.elastic.apm.agent.micrometer;

import co.elastic.apm.agent.configuration.MetricsConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.mock;

class MicrometerMeterRegistrySerializerTest {

    @Test
    void serializeEmpty() {
        MetricsConfiguration config = mock(MetricsConfiguration.class);
        MicrometerMeterRegistrySerializer serializer = new MicrometerMeterRegistrySerializer(config);

        serializer.serialize(Collections.emptyMap(), 0);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        Counter counter = Counter.builder("counter").tags("tag", "value").register(registry);
        counter.increment(42);

        serializer.serialize(Map.of(counter.getId(), counter), 0L);

    }

}
