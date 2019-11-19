package co.elastic.apm.agent.redis.lettuce;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class Lettuce4VersionsIT {

    private final TestClassWithDependencyRunner runner;

    public Lettuce4VersionsIT(String group, String artifact, String version) throws Exception {
        runner = new TestClassWithDependencyRunner(group, artifact, version, Lettuce4InstrumentationTest.class);
    }

    @Parameterized.Parameters(name= "{0}:{1}:{2}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
//            { "biz.paluch.redis", "lettuce", "4.5.0.Final" },
//            { "biz.paluch.redis", "lettuce", "4.4.6.Final" },
//            { "biz.paluch.redis", "lettuce", "4.3.3.Final" },
//            { "biz.paluch.redis", "lettuce", "4.2.2.Final" },
            { "biz.paluch.redis", "lettuce", "4.1.2.Final" },
            { "biz.paluch.redis", "lettuce", "4.0.2.Final" },
//            { "biz.paluch.redis", "lettuce", "3.5.0.Final" },
//            { "biz.paluch.redis", "lettuce", "3.4.3.Final" },
//            { "biz.paluch.redis", "lettuce", "3.3.2.Final" },
//            { "biz.paluch.redis", "lettuce", "3.2.Final" },
//            { "biz.paluch.redis", "lettuce", "3.1.Final" },
//            { "biz.paluch.redis", "lettuce", "3.0.3.Final" },
        });
    }

    @Test
    public void testLettuce() {
        runner.run();
    }
}
