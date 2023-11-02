package co.elastic.apm.agent.opentelemetry.tracing;

import co.elastic.apm.agent.impl.transaction.OTelSpanKind;
import io.opentelemetry.api.trace.SpanKind;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class OTelHelper {
    private OTelHelper() {}

    @Nonnull
    public static OTelSpanKind map(@Nullable SpanKind kind) {
        if (kind == null) {
            return OTelSpanKind.INTERNAL;
        } else {
            return OTelSpanKind.valueOf(kind.name());
        }
    }
}
