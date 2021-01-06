package co.elastic.apm.agent.sdk.state;

import org.junit.jupiter.api.Test;

public class GlobalThreadLocalTest {

    GlobalThreadLocal<String> threadLocal = GlobalThreadLocal.get(GlobalThreadLocalTest.class, "test");

    @Test
    void setNullValueShouldNotThrow() {
        threadLocal.set(null);
    }
}
