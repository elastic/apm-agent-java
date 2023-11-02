package co.elastic.apm.agent.opentelemetry.tracing;

import co.elastic.apm.agent.impl.transaction.OTelSpanKind;
import io.opentelemetry.api.trace.SpanKind;
import org.junit.Test;

import java.util.Arrays;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class OTelHelperTest {

    @Test
    public void testMappingOfAllValues() {
        Arrays.asList(SpanKind.values())
            .forEach(otelKind -> {
                OTelSpanKind oTelSpanKind = null;
                try {
                    oTelSpanKind = OTelHelper.map(otelKind);
                    assertThat(oTelSpanKind).isNotNull();
                    assertThat(oTelSpanKind.name()).isEqualTo(otelKind.toString());
                } catch (NoSuchElementException e) {
                    fail(String.format("Exception should not be thrown with otelKind %s. Please check your OTelSpanKind class with new values.", otelKind));
                }
            });
    }

    @Test
    public void testMappingOfNullValue() {
        OTelSpanKind oTelSpanKind = OTelHelper.map(null);

        assertThat(oTelSpanKind).isNotNull().isEqualTo(OTelSpanKind.INTERNAL);
    }
}
