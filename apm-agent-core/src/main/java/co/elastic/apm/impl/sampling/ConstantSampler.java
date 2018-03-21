package co.elastic.apm.impl.sampling;

import co.elastic.apm.impl.transaction.TransactionId;

/**
 * This is a implementation of {@link Sampler} which always returns the same sampling decision.
 */
public class ConstantSampler implements Sampler {

    private static final Sampler TRUE = new ConstantSampler(true);
    private static final Sampler FALSE = new ConstantSampler(false);

    private final boolean decision;

    private ConstantSampler(boolean decision) {
        this.decision = decision;
    }

    public static Sampler of(boolean decision) {
        if (decision) {
            return TRUE;
        } else {
            return FALSE;
        }
    }

    @Override
    public boolean isSampled(TransactionId transactionId) {
        return decision;
    }
}
