package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface ServiceTarget {
    ServiceTarget withType(@Nullable String type);

    ServiceTarget withName(@Nullable CharSequence name);

    ServiceTarget withUserName(@Nullable CharSequence name);

    ServiceTarget withHostPortName(@Nullable CharSequence host, int port);

    ServiceTarget withNameOnlyDestinationResource();
}
