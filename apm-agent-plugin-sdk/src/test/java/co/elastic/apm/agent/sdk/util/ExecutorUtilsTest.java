package co.elastic.apm.agent.sdk.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorUtilsTest {

    @Test
    void createsAndShutsDownExecutor() {
        ScheduledExecutorService service = ExecutorUtils.createSingleThreadSchedulingDaemonPool("purpose");
        assertThat(ExecutorUtils.isAgentExecutor(service)).isTrue();
        ExecutorUtils.shutdownAndWaitTermination(service);
    }
}
