package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class OkHttp3ClientVersionIT {
    private final TestClassWithDependencyRunner runner;

    public OkHttp3ClientVersionIT(List<String> dependencies) throws Exception {
        this.runner = new TestClassWithDependencyRunner(dependencies,
            OkHttp3ClientInstrumentationTest.class);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            // ivy can't automatically resolve the dependencies, so we'll have to manually type them in
            // check https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp for the respective versions of kotlin and okio
            {List.of("com.squareup.okhttp3:okhttp:4.3.0", "org.jetbrains.kotlin:kotlin-stdlib:1.3.61", "com.squareup.okio:okio:2.4.1")},
            {List.of("com.squareup.okhttp3:okhttp:4.4.1", "org.jetbrains.kotlin:kotlin-stdlib:1.3.61", "com.squareup.okio:okio:2.4.3")},
            {List.of("com.squareup.okhttp3:okhttp:4.5.0", "org.jetbrains.kotlin:kotlin-stdlib:1.3.70", "com.squareup.okio:okio:2.5.0")},
            {List.of("com.squareup.okhttp3:okhttp:4.6.0", "org.jetbrains.kotlin:kotlin-stdlib:1.3.71", "com.squareup.okio:okio:2.6.0")},
            {List.of("com.squareup.okhttp3:okhttp:4.7.1", "org.jetbrains.kotlin:kotlin-stdlib:1.3.71", "com.squareup.okio:okio:2.6.0")},
            {List.of("com.squareup.okhttp3:okhttp:4.8.1", "org.jetbrains.kotlin:kotlin-stdlib:1.3.72", "com.squareup.okio:okio:2.7.0")},
            {List.of("com.squareup.okhttp3:okhttp:4.9.1", "org.jetbrains.kotlin:kotlin-stdlib:1.4.10", "com.squareup.okio:okio:2.8.0")},
        });
    }

    @Test
    public void testVersions() {
        runner.run();
    }
}
