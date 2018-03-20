package co.elastic.apm.impl.sampling;

import co.elastic.apm.impl.transaction.TransactionId;

/**
 * A sampler is responsible for determining whether a {@link co.elastic.apm.api.Transaction} should be sampled.
 * <p>
 * In contrast other tracing systems,
 * in Elastic APM,
 * non-sampled {@link co.elastic.apm.api.Transaction}s do get reported to the APM server.
 * However,
 * to keep the size at a minimum,
 * the reported {@link co.elastic.apm.api.Transaction} only contains the transaction name,
 * the duration and the id.
 * Also,
 * {@link co.elastic.apm.api.Span}s of non sampled {@link co.elastic.apm.api.Transaction}s are not reported.
 * </p>
 */
public interface Sampler {

    /**
     * Determines whether the given transaction should be sampled.
     *
     * @param transactionId The id of the transaction.
     * @return The sampling decision.
     */
    boolean isSampled(TransactionId transactionId);
}
