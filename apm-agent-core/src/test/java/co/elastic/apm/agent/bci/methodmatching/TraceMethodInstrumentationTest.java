/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.bci.methodmatching;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class TraceMethodInstrumentationTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        when(config.getConfig(CoreConfiguration.class).getTraceMethods()).thenReturn(Arrays.asList(
            MethodMatcher.of("private co.elastic.apm.agent.bci.methodmatching.TraceMethodInstrumentationTest$TestClass#traceMe*()"),
            MethodMatcher.of("co.elastic.apm.agent.bci.methodmatching.TraceMethodInstrumentationTest$TestExcludeConstructor#*"))
        );
        when(config.getConfig(CoreConfiguration.class).getMethodsExcludedFromInstrumentation())
            .thenReturn(Collections.singletonList(WildcardMatcher.valueOf("*exclude*")));
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @AfterEach
    void tearDown() {
        ElasticApmAgent.reset();
    }

    @Test
    void testTraceMethod() {
        TestClass.traceMe();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getName().toString()).isEqualTo("TestClass#traceMe");
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getName().toString()).isEqualTo("TestClass#traceMeToo");
    }

    @Test
    void testExcludedMethod() {
        TestClass.traceMeAsWell();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getName().toString()).isEqualTo("TestClass#traceMeAsWell");
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().getName().toString()).isEqualTo("TestClass#traceMeToo");
    }

    @Test
    void testNotMatched_VisibilityModifier() {
        TestClass.traceMeNot();
        assertThat(reporter.getTransactions()).isEmpty();
    }

    @Test
    void testNotMatched_Parameters() {
        TestClass.traceMeNot(false);
        assertThat(reporter.getTransactions()).isEmpty();
    }

    // Byte Buddy can't catch exceptions (onThrowable = Throwable.class) during a constructor call:
    @Test
    void testNotMatched_Constructor() {
        final TestExcludeConstructor testClass = new TestExcludeConstructor();
        // the static initializer should not be traced
        assertThat(reporter.getTransactions()).isEmpty();
        testClass.traceMe();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getName().toString()).isEqualTo("TestExcludeConstructor#traceMe");
    }

    public static class TestClass {
        // not traced because visibility modifier does not match
        public static void traceMeNot() {
        }

        private static void traceMeNot(boolean doesNotMatchParameterMatcher) {
        }

        private static void traceMe() {
            traceMeToo();
        }

        private static void traceMeAsWell() {
            traceMeToo();
            traceMeNotExcludeMe();
        }

        private static void traceMeToo() {

        }

        private static void traceMeNotExcludeMe() {

        }
    }

    public static class TestExcludeConstructor {
        static {
        }

        public TestExcludeConstructor() {
        }

        public void traceMe() {
        }
    }

}
