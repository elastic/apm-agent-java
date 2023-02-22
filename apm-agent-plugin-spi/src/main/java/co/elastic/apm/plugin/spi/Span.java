package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public interface Span<T extends Span<T>> extends AbstractSpan<T> {

    long MAX_LOG_INTERVAL_MICRO_SECS = TimeUnit.MINUTES.toMicros(5);

    SpanContext getContext();

    @Nullable
    String getSubtype();

    boolean isExit();

    @Nullable
    String getAction();

    T withAction(@Nullable String action);

    T withSubtype(@Nullable String subtype);

    @Deprecated
    void setType(@Nullable String type, @Nullable String subtype, @Nullable String action);
}
