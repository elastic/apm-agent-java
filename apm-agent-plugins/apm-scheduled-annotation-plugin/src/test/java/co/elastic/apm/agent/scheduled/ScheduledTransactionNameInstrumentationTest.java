package co.elastic.apm.agent.scheduled;

import javax.ejb.Schedule;

public class ScheduledTransactionNameInstrumentationTest extends AbstractScheduledTransactionNameInstrumentationTest{
    @Override
    JeeCounter createJeeCounterImpl() {
        return new JeeCounterImpl();
    }

    @Override
    ThrowingCounter createThrowingCounterImpl() {
        return new ThrowingCounterImpl();
    }

    protected static class JeeCounterImpl extends JeeCounter {

        @Schedule(minute = "5")
        public void scheduled() {
            this.count.incrementAndGet();
        }

        @javax.ejb.Schedules({
            @Schedule(minute = "5"),
            @Schedule(minute = "10")
        })
        public void scheduledJava7Repeatable() {
            this.count.incrementAndGet();
        }
    }

    protected static class ThrowingCounterImpl extends ThrowingCounter {

        @Schedule(minute = "5") // whatever the used annotation here, the behavior should be the same
        public void throwingException() {
            count.incrementAndGet();
            throw new RuntimeException("intentional exception");
        }
    }
}
