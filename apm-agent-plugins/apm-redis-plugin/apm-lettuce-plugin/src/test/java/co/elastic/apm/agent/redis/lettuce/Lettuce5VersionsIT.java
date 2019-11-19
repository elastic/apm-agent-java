package co.elastic.apm.agent.redis.lettuce;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class Lettuce5VersionsIT {

    private final TestClassWithDependencyRunner runner;

    public Lettuce5VersionsIT(String group, String artifact, String version) throws Exception {
        runner = new TestClassWithDependencyRunner(group, artifact, version, Lettuce5InstrumentationTest.class);
    }

    @Parameterized.Parameters(name= "{0}:{1}:{2}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "io.lettuce", "lettuce-core", "5.2.1.RELEASE" },
            { "io.lettuce", "lettuce-core", "5.1.8.RELEASE" },
            { "io.lettuce", "lettuce-core", "5.0.5.RELEASE" },
        });
    }

    @Test
    public void testLettuce() {
        runner.run();
    }
}
