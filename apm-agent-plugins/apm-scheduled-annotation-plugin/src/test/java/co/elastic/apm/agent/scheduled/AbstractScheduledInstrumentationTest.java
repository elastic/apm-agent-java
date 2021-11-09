package co.elastic.apm.agent.scheduled;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractScheduledInstrumentationTest extends AbstractInstrumentationTest {

    protected static List<Transaction> checkTransactions(AbstractCounter counter, int expectedCount, String expectedName) {
        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(counter.getInvocationCount()).hasSize(expectedCount);
        transactions.forEach(t -> {
            assertThat(t.getNameAsString()).isEqualTo(expectedName);
        });
        return transactions;
    }

    protected static void checkOutcome(List<Transaction> transactions, Outcome outcome) {
        assertThat(transactions.stream()
            .map(AbstractSpan::getOutcome)
            .collect(Collectors.toSet()))
            .containsExactly(outcome);
    }

    protected static abstract class AbstractCounter {
        protected final AtomicInteger count = new AtomicInteger(0);

        public final int getInvocationCount() {
            return this.count.get();
        }
    }
}
