package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class LegacyApacheHttpClientVersionIT {

    private final TestClassWithDependencyRunner runner;


    public LegacyApacheHttpClientVersionIT(List<String> dependencies) throws Exception {
        this.runner = new TestClassWithDependencyRunner(dependencies, "co.elastic.apm.agent.httpclient.LegacyApacheHttpClientInstrumentationTest");
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {List.of("org.apache.httpcomponents:httpcore:4.0")},
            {List.of("org.apache.httpcomponents:httpcore:4.0.1")},
            {List.of("org.apache.httpcomponents:httpcore:4.1.4")},
            {List.of("org.apache.httpcomponents:httpcore:4.2.5")}
        });
    }

    @Test
    public void test() {
        runner.run();
    }
}
