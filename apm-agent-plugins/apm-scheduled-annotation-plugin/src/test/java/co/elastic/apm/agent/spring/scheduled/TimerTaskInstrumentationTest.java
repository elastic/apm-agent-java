package co.elastic.apm.agent.spring.scheduled;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import org.junit.jupiter.api.Test;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TimerTaskInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testTimerTaskWithScheduleAtFixedRate() throws InterruptedException {
        reporter.reset();
        TestTimerTask timerTask = new TestTimerTask();
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(timerTask, 0, 5);
        Thread.sleep(100);
        timer.cancel();

        assertThat(reporter.getTransactions().size()).isEqualTo(timerTask.getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("TestTimerTask#run");
    }

    @Test
    void testTimerTaskWithSchedule() throws InterruptedException {
        reporter.reset();
        TestTimerTask timerTask = new TestTimerTask();
        Timer timer = new Timer(true);
        timer.schedule(timerTask, 0, 5);
        Thread.sleep(100);
        timer.cancel();

        assertThat(reporter.getTransactions().size()).isEqualTo(timerTask.getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("TestTimerTask#run");
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
