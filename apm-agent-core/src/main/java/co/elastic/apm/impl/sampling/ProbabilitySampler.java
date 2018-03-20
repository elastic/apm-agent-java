package co.elastic.apm.impl.sampling;

import co.elastic.apm.impl.transaction.TransactionId;

public class ProbabilitySampler implements Sampler {

    private final long lowerBound;
    private final long higherBound;

    public static Sampler of(double samplingRate) {
        if (samplingRate == 1) {
            return ConstantSampler.of(true);
        }
        if (samplingRate == 0) {
            return ConstantSampler.of(false);
        }
        return new ProbabilitySampler(samplingRate);
    }

    private ProbabilitySampler(double samplingRate) {
        higherBound = (long) (Long.MAX_VALUE * samplingRate);
        lowerBound = -higherBound;
    }

    @Override
    public boolean isSampled(TransactionId transactionId) {
        final long mostSignificantBits = transactionId.getMostSignificantBits();
        return mostSignificantBits > lowerBound && mostSignificantBits < higherBound;
    }
}
