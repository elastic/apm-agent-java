package co.elastic.apm.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

abstract class AbstractSpanImpl extends BaseAbstractSpanImpl<Span> implements Span {
    AbstractSpanImpl(@Nonnull Object span) {
        super(span);
    }

    @Nonnull
    @Override
    public Span withDestinationServiceResource(String resource) {
        doAppendDestinationServiceResource(resource);
        return this;
    }

    @Nonnull
    @Override
    public Span withDestinationServiceName(String name) {
        doAppendDestinationServiceName(name);
        return this;
    }

    @Nonnull
    @Override
    public Span setDestinationServiceType(String type) {
        doSetDestinationServiceType(type);
        return this;
    }

    private void doAppendDestinationServiceResource(@Nullable String resource) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$WithDestinationServiceResourceInstrumentation
    }

    private void doAppendDestinationServiceName(@Nullable String name) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$WithDestinationServiceNameInstrumentation
    }

    private void doSetDestinationServiceType(@Nullable String type) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$WithDestinationServiceTypeInstrumentation
    }
}
