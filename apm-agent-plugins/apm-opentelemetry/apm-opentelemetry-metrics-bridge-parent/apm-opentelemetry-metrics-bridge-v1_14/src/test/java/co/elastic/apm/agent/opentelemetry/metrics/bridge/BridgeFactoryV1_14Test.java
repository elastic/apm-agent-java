package co.elastic.apm.agent.opentelemetry.metrics.bridge;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class BridgeFactoryV1_14Test {

    @Test
    void checkAttributeCachesLimited() {
        List<Attributes> attributes = LongStream.range(0, 10_000)
            .mapToObj(i -> Attributes.of(AttributeKey.longKey("foo-" + i), i))
            .collect(Collectors.toList());

        BridgeFactoryV1_14 bridge = new BridgeFactoryV1_14();
        attributes.forEach(bridge::convertAttributes);

        assertThat(bridge.convertedAttributes.approximateSize()).isEqualTo(BridgeFactoryV1_14.MAX_ATTRIBUTE_CACHE_SIZE);
        assertThat(bridge.convertedAttributeKeys.approximateSize()).isEqualTo(BridgeFactoryV1_14.MAX_ATTRIBUTE_KEY_CACHE_SIZE);
    }

}
