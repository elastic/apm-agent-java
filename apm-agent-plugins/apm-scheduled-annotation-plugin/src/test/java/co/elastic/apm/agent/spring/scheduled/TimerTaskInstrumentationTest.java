package co.elastic.apm.agent.spring.scheduled;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import org.junit.jupiter.api.Test;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TimerTaskInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testTimerTask_scheduleWithFixedRate() throws InterruptedException {
        reporter.reset();
        TestTimerTask timerTask = new TestTimerTask();
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(timerTask, 0, 10L);

        Thread.sleep(100L);
        timer.cancel();
        Thread.sleep(100L);

        assertThat(reporter.getTransactions().size()).isEqualTo(timerTask.getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("TestTimerTask#run");
    }

    @Test
    void testTimerTask_scheduleWithFixedDelay() throws InterruptedException {
        reporter.reset();
        TestTimerTask timerTask = new TestTimerTask();
        Timer timer = new Timer("Timer");
        timer.schedule(timerTask, 1L, 10L);

        Thread.sleep(100L);
        timer.cancel();
        Thread.sleep(100L);

        assertThat(reporter.getTransactions().size()).isEqualTo(timerTask.getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("TestTimerTask#run");
    }

    @Test
    void testTimerTask_scheduleOnce() throws InterruptedException {
        reporter.reset();
        TestTimerTask timerTask = new TestTimerTask();
        Timer timer = new Timer("Timer");
        long delay = 50L;
        timer.schedule(timerTask, delay);

        Thread.sleep(2 * delay);

        assertThat(reporter.getTransactions().size()).isEqualTo(1);
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("TestTimerTask#run");
    }

    @Test
    void testTimerTask_withAnonymousClass() throws InterruptedException {
        reporter.reset();
        AtomicInteger count = new AtomicInteger(0);

        TimerTask repeatedTask = new TimerTask() {
            public void run() {
                count.incrementAndGet();
            }
        };
        Timer timer = new Timer("Timer");
        long delay = 50L;
        timer.schedule(repeatedTask, delay);

        Thread.sleep(2 * delay);

        assertThat(reporter.getTransactions().size()).isEqualTo(1);
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("1#run");
    }

    public static class TestTimerTask extends TimerTask {
        private AtomicInteger count = new AtomicInteger(0);

        @Override
        public void run() {
            this.count.incrementAndGet();
        }

        public int getInvocationCount() {
            return this.count.get();
        }
    }
}
