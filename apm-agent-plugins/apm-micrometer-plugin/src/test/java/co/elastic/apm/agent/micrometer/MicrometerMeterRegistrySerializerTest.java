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
