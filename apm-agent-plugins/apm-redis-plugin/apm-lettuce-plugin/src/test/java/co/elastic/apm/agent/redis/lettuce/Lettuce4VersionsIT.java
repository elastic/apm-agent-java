package co.elastic.apm.agent.redis.lettuce;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class Lettuce4VersionsIT {

    private final TestClassWithDependencyRunner runner;

    public Lettuce4VersionsIT(List<String> dependencies) throws Exception {
        System.setProperty("io.lettuce.core.kqueue", "false");
        runner = new TestClassWithDependencyRunner(dependencies, Lettuce4InstrumentationTest.class);
    }

    @Parameterized.Parameters(name= "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { List.of("biz.paluch.redis:lettuce:4.5.0.Final", "io.netty:netty-all:4.1.29.Final") },
            { List.of("biz.paluch.redis:lettuce:4.4.6.Final", "io.netty:netty-all:4.1.24.Final") },
            { List.of("biz.paluch.redis:lettuce:4.3.3.Final", "io.netty:netty-all:4.1.13.Final") },
            { List.of("biz.paluch.redis:lettuce:4.2.2.Final", "io.netty:netty-all:4.0.40.Final") },
            { List.of("biz.paluch.redis:lettuce:4.1.2.Final", "io.netty:netty-all:4.0.34.Final", "org.latencyutils:LatencyUtils:2.0.3") },
            { List.of("biz.paluch.redis:lettuce:4.0.2.Final", "io.netty:netty-all:4.0.30.Final") },
        });
    }

    @Test
    public void testLettuce() {
        runner.run();
    }
}
