package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.transaction.OTelSpanKind;
import io.opentelemetry.api.trace.SpanKind;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class OTelSpanKindTest {

    @Test
    void checkSpanKindMapping() {
        assertThat(Stream.of(OTelSpanKind.values()).map(Enum::name).collect(Collectors.toSet()))
            .containsExactlyElementsOf(Stream.of(SpanKind.values()).map(Enum::name).collect(Collectors.toSet()));

    }
}
