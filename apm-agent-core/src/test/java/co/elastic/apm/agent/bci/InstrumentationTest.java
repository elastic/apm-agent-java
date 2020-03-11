/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.commons.math.util.MathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class InstrumentationTest {

    private final ElasticApmTracer tracer = MockTracer.create();

    @AfterEach
    void afterAll() {
        ElasticApmAgent.reset();
    }

    @Test
    void testIntercept() {
        init(SpyConfiguration.createSpyConfig(), List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testDisabled() {
        final ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        when(config.getConfig(CoreConfiguration.class).getDisabledInstrumentations()).thenReturn(Collections.singletonList("test"));
        init(config, List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testEnsureInstrumented() {
        init(SpyConfiguration.createSpyConfig(), List.of());
        assertThat(interceptMe()).isEmpty();
        ElasticApmAgent.ensureInstrumented(getClass(), List.of(TestInstrumentation.class));
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testReInitEnableOneInstrumentation() {
        final ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        CoreConfiguration configConfig = config.getConfig(CoreConfiguration.class);
        when(configConfig.getDisabledInstrumentations()).thenReturn(Collections.singletonList("test"));
        init(config, List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();

        when(configConfig.getDisabledInstrumentations()).thenReturn(List.of());
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testReInitDisableAllInstrumentations() {
        final ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        init(config, List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");

        when(config.getConfig(CoreConfiguration.class).isInstrument()).thenReturn(false);
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testDontInstrumentOldClassFileVersions() {
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new MathInstrumentation()));
        // if the instrumentation applied, it would return 42
        // but instrumenting old class file versions could lead to VerifyErrors in some cases and possibly some more shenanigans
        // so we we are better off not touching Java 1.4 code at all
        assertThat(MathUtils.sign(-42)).isEqualTo(-1);
    }

    @Test
    void testSuppressException() {
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new SuppressExceptionInstrumentation()));
        assertThat(noExceptionPlease("foo")).isEqualTo("foo_no_exception");
    }

    @Test
    void testRetainExceptionInUserCode() {
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new SuppressExceptionInstrumentation()));
        assertThatThrownBy(this::exceptionPlease).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNonSuppressedException() {
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new ExceptionInstrumentation()));
        assertThatThrownBy(() -> noExceptionPlease("foo")).isInstanceOf(RuntimeException.class);
    }

    String noExceptionPlease(String s) {
        return s + "_no_exception";
    }

    String exceptionPlease() {
        throw null;
    }

    private void init(ConfigurationRegistry config, List<ElasticApmInstrumentation> instrumentations) {
        ElasticApmAgent.initInstrumentation(new ElasticApmTracerBuilder()
                .configurationRegistry(config)
                .build(),
            ByteBuddyAgent.install(),
            instrumentations);
    }

    private String interceptMe() {
        return "";
    }

    public static class TestInstrumentation extends ElasticApmInstrumentation {
        @Advice.OnMethodExit
        public static void onMethodExit(@Advice.Return(readOnly = false) String returnValue) {
            returnValue = "intercepted";
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return ElementMatchers.named("co.elastic.apm.agent.bci.InstrumentationTest");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return ElementMatchers.named("interceptMe");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singleton("test");
        }
    }

    public static class MathInstrumentation extends ElasticApmInstrumentation {
        @Advice.OnMethodExit
        public static void onMethodExit(@Advice.Return(readOnly = false) int returnValue) {
            returnValue = 42;
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return ElementMatchers.named("org.apache.commons.math.util.MathUtils");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return ElementMatchers.named("sign").and(ElementMatchers.takesArguments(int.class));
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.emptyList();
        }
    }

    public static class ExceptionInstrumentation extends ElasticApmInstrumentation {
        @Advice.OnMethodExit
        public static void onMethodExit() {
            throw new RuntimeException("This exception should not be suppressed");
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return ElementMatchers.named(InstrumentationTest.class.getName());
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return ElementMatchers.nameEndsWithIgnoreCase("exceptionPlease");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.emptyList();
        }
    }

    public static class SuppressExceptionInstrumentation extends ElasticApmInstrumentation {
        @Advice.OnMethodExit(suppress = Throwable.class)
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onMethodEnterAndExit() {
            throw new RuntimeException("This exception should be suppressed");
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return ElementMatchers.named(InstrumentationTest.class.getName());
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return ElementMatchers.named("noExceptionPlease");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.emptyList();
        }
    }
}
