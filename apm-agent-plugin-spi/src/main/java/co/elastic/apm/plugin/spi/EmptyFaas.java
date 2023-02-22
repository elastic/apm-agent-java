package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public class EmptyFaas implements Faas {

    public static final Faas INSTANCE = new EmptyFaas();

    private EmptyFaas() {
    }

    @Override
    public Faas withColdStart(boolean coldStart) {
        return this;
    }

    @Override
    public Faas withId(@Nullable String id) {
        return this;
    }

    @Override
    public Faas withName(@Nullable String name) {
        return this;
    }

    @Override
    public Faas withVersion(@Nullable String version) {
        return this;
    }

    @Override
    public Faas withExecution(@Nullable String execution) {
        return this;
    }
}
