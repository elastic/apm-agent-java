package co.elastic.apm.agent.redis.lettuce;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class Lettuce3VersionsIT {

    private final TestClassWithDependencyRunner runner;

    public Lettuce3VersionsIT(List<String> dependencies) throws Exception {
        System.setProperty("io.lettuce.core.kqueue", "false");
        runner = new TestClassWithDependencyRunner(dependencies, Lettuce3InstrumentationTest.class);
    }

    @Parameterized.Parameters(name= "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { List.of("biz.paluch.redis:lettuce:3.5.0.Final", "io.netty:netty-all:4.0.37.Final") },
            { List.of("biz.paluch.redis:lettuce:3.4.3.Final", "io.netty:netty-all:4.0.34.Final", "org.latencyutils:LatencyUtils:2.0.2") },
        });
    }

    @Test
    public void testLettuce() {
        runner.run();
    }
}
