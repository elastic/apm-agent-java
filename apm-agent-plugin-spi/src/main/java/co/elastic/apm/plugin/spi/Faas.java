package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface Faas {

    Faas withColdStart(boolean coldStart);

    Faas withId(@Nullable String id);

    Faas withName(@Nullable String name);

    Faas withVersion(@Nullable String version);

    Faas withExecution(@Nullable String execution);
}
