package co.elastic.apm.agent.scheduled;

import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;

import java.util.List;

public class SpringScheduledTransactionNameInstrumentationTest extends AbstractScheduledInstrumentationTest {

    @Test
    void testSpringScheduledAnnotatedMethodsAreTraced() {
        SpringCounter springCounter = new SpringCounter();
        springCounter.scheduled();
        springCounter.scheduled();

        List<Transaction> transactions = checkTransactions(springCounter, 2, "SpringCounter#scheduled");
        checkOutcome(transactions, Outcome.SUCCESS);
    }

    @Test
    void testSpringJ8RepeatableScheduledAnnotatedMethodsAreTraced() {
        SpringCounter springCounter = new SpringCounter();
        springCounter.scheduledJava8Repeatable();
        springCounter.scheduledJava8Repeatable();

        List<Transaction> transactions = checkTransactions(springCounter, 2, "SpringCounter#scheduledJava8Repeatable");
        checkOutcome(transactions, Outcome.SUCCESS);
    }

    @Test
    void testSpringJ7RepeatableScheduledAnnotatedMethodsAreTraced() {
        SpringCounter springCounter = new SpringCounter();
        springCounter.scheduledJava7Repeatable();
        springCounter.scheduledJava7Repeatable();

        List<Transaction> transactions = checkTransactions(springCounter, 2, "SpringCounter#scheduledJava7Repeatable");
        checkOutcome(transactions, Outcome.SUCCESS);
    }

    private static class SpringCounter extends AbstractCounter {

        @Scheduled(fixedDelay = 5)
        public void scheduled() {
            this.count.incrementAndGet();
        }

        @Scheduled(fixedDelay = 5)
        @Scheduled(fixedDelay = 10)
        public void scheduledJava8Repeatable() {
            this.count.incrementAndGet();
        }

        @Schedules({
            @Scheduled(fixedDelay = 5),
            @Scheduled(fixedDelay = 10)
        })
        public void scheduledJava7Repeatable() {
            this.count.incrementAndGet();
        }

    }

}
