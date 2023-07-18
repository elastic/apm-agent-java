package co.elastic.apm.agent.sdk.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ThreadUtilTest {

    @Test
    public void checkPlatformThreadVirtual() {
        Thread t1 = new Thread();
        assertThat(ThreadUtil.isVirtual(t1)).isFalse();
    }

    @Test
    @DisabledForJreRange(max = JRE.JAVA_20)
    public void checkVirtualThreadVirtual() throws Exception {
        Runnable task = () -> {
        };
        Thread thread = (Thread) Thread.class.getMethod("startVirtualThread", Runnable.class).invoke(null, task);
        assertThat(ThreadUtil.isVirtual(thread)).isTrue();
    }
}
