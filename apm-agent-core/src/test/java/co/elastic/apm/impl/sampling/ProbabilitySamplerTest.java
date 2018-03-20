package co.elastic.apm.impl.sampling;

import co.elastic.apm.impl.transaction.TransactionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProbabilitySamplerTest {

    public static final int ITERATIONS = 1_000_000;
    public static final int DELTA = (int) (ITERATIONS * 0.01);
    public static final double SAMPLING_RATE = 0.5;
    private Sampler sampler;

    @BeforeEach
    void setUp() {
        sampler = ProbabilitySampler.of(SAMPLING_RATE);
    }

    @Test
    void isSampledEmpiricalTest() {
        int sampledTransactions = 0;
        TransactionId id = new TransactionId();
        for (int i = 0; i < ITERATIONS; i++) {
            id.setToRandomValue();
            if (sampler.isSampled(id)) {
                sampledTransactions++;
            }
        }
        assertThat(sampledTransactions).isBetween((int) (SAMPLING_RATE * ITERATIONS - DELTA), (int) (SAMPLING_RATE * ITERATIONS + DELTA));
    }

    @Test
    void testSamplingUpperBoundary() {
        long upperBound = Long.MAX_VALUE / 2;
        final TransactionId transactionId = new TransactionId();

        transactionId.setValue(upperBound - 1, 0);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isTrue();

        transactionId.setValue(upperBound, 0);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isTrue();

        transactionId.setValue(upperBound + 1, 0);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isFalse();
    }

    @Test
    void testSamplingLowerBoundary() {
        long lowerBound = -Long.MAX_VALUE / 2;
        final TransactionId transactionId = new TransactionId();

        transactionId.setValue(lowerBound + 1, 0);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isTrue();

        transactionId.setValue(lowerBound, 0);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isTrue();

        transactionId.setValue(lowerBound - 1, 0);
        assertThat(ProbabilitySampler.of(0.5).isSampled(transactionId)).isFalse();
    }

}
