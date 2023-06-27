package co.elastic.apm.agent.impl.baggage;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.ElasticContext;
import co.elastic.apm.agent.tracer.BaggageContextBuilder;
import co.elastic.apm.agent.tracer.Scope;

import javax.annotation.Nullable;

public class BaggageContext extends ElasticContext<BaggageContext> {

    @Nullable
    private final AbstractSpan<?> span;
    private final Baggage baggage;

    private BaggageContext(@Nullable AbstractSpan<?> span, Baggage baggage) {
        super(tracer);
        this.span = span;
        this.baggage = baggage;
    }

    @Nullable
    @Override
    public AbstractSpan<?> getSpan() {
        return span;
    }

    @Override
    public Baggage getBaggage() {
        return baggage;
    }

    @Override
    public BaggageContext activate() {
        return null;
    }

    @Override
    public BaggageContext deactivate() {
        return null;
    }

    @Override
    public Scope activateInScope() {
        return null;
    }

    @Override
    public void incrementReferences() {

    }

    @Override
    public void decrementReferences() {

    }

    public static Baggage.Builder createBuilder(ElasticContext<?> parent) {
        return new Builder()
    }

    public static class Builder implements BaggageContextBuilder {

        private final AbstractSpan<?> span;

        private final Baggage.Builder baggageBuilder;

        public Builder(AbstractSpan<?> span, Baggage baseBaggage) {
            this.span = span;
            baggageBuilder = baseBaggage.toBuilder();
        }

        @Override
        public void put(String key, @Nullable String value) {
            baggageBuilder.put(key, value);
        }

        @Override
        public void remove(String key) {
            baggageBuilder.put(key, null);
        }

        @Override
        public BaggageContext buildContext() {
            return new BaggageContext(span, baggageBuilder.build());
        }
    }
}
