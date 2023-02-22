package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface HeaderChildContextCreator<H, C> {
    boolean asChildOf(TraceContext child, @Nullable C carrier, HeaderGetter<H, C> headerGetter);
}
