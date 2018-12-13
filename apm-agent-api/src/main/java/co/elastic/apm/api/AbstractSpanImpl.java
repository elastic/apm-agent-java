package co.elastic.apm.api;

import javax.annotation.Nonnull;

public abstract class AbstractSpanImpl implements Span {
    @Nonnull
    // co.elastic.apm.impl.transaction.AbstractSpan
    protected final Object span;

    AbstractSpanImpl(@Nonnull Object span) {
        this.span = span;
    }

    @Nonnull
    @Override
    public Span createSpan() {
        Object span = doCreateSpan();
        return span != null ? new SpanImpl(span) : NoopSpan.INSTANCE;
    }

    private Object doCreateSpan() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$DoCreateSpanInstrumentation.doCreateSpan
        return null;
    }

    void doSetName(String name) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$SetNameInstrumentation.doSetName
    }

    void doSetType(String type) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$SetTypeInstrumentation.doSetType
    }

    void doAddTag(String key, String value) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$AddTagInstrumentation.doAddTag
    }

    @Override
    public void end() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation$EndInstrumentation.end
    }

    @Override
    public void captureException(Throwable throwable) {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation.CaptureExceptionInstrumentation
    }

    @Nonnull
    @Override
    public String getId() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation.GetIdInstrumentation
        return "";
    }

    @Nonnull
    @Override
    public String getTraceId() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation.GetTraceIdInstrumentation
        return "";
    }

    @Override
    public Scope activate() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation.ActivateInstrumentation
        return new ScopeImpl(span);
    }

    @Override
    public boolean isSampled() {
        // co.elastic.apm.agent.plugin.api.AbstractSpanInstrumentation.IsSampledInstrumentation
        return false;
    }
}
