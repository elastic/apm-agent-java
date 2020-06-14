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
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToArgument;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToField;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToReturn;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.util.GlobalVariables;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math.util.MathUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.pool2.impl.CallStackUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.SubstituteLoggingEvent;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

class InstrumentationTest {

    private ElasticApmTracer tracer;
    private ConfigurationRegistry configurationRegistry;
    private CoreConfiguration coreConfig;
    private String privateString;

    @BeforeEach
    void setup() {
        tracer = MockTracer.create();
        configurationRegistry = SpyConfiguration.createSpyConfig();
        coreConfig = configurationRegistry.getConfig(CoreConfiguration.class);
    }

    @AfterEach
    void reset() {
        MockTracer.resetTracer();
    }

    @Test
    void testIntercept() {
        init(configurationRegistry, List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testFieldAccess() {
        init(configurationRegistry, List.of(new FieldAccessInstrumentation()));
        assignToField("@AssignToField");
        assertThat(privateString).isEqualTo("@AssignToField");
    }

    @Test
    void testFieldAccessArray() {
        init(configurationRegistry, List.of(new FieldAccessArrayInstrumentation()));
        assignToField("@AssignToField");
        assertThat(privateString).isEqualTo("@AssignToField");
    }

    public void assignToField(String s) {
    }

    @Test
    void testAssignToArgument() {
        init(configurationRegistry, List.of(new AssignToArgumentInstrumentation()));
        assertThat(assignToArgument("foo")).isEqualTo("foo@AssignToArgument");
    }

    public String assignToArgument(String s) {
        return s;
    }

    @Test
    void testAssignToArgumentArray() {
        init(configurationRegistry, List.of(new AssignToArgumentsInstrumentation()));
        assertThat(assignToArguments("foo", "bar")).isEqualTo("barfoo");
    }

    public String assignToArguments(String foo, String bar) {
        return foo + bar;
    }

    @Test
    void testAssignToReturnArray() {
        init(configurationRegistry, List.of(new AssignToReturnArrayInstrumentation()));
        assertThat(assignToReturn("foo", "bar")).isEqualTo("foobar");
    }

    @Nullable
    public String assignToReturn(String foo, String bar) {
        return null;
    }

    @Test
    void testDisabled() {
        when(coreConfig.getDisabledInstrumentations()).
            thenReturn(Collections.singletonList("test"));
        init(configurationRegistry, List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testEnsureInstrumented() {
        init(configurationRegistry, List.of());
        assertThat(interceptMe()).isEmpty();
        ElasticApmAgent.ensureInstrumented(getClass(), List.of(TestInstrumentation.class));
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testReInitEnableOneInstrumentation() {
        when(coreConfig.getDisabledInstrumentations()).thenReturn(Collections.singletonList("test"));
        init(configurationRegistry, List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();

        when(coreConfig.getDisabledInstrumentations()).thenReturn(List.of());
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testDefaultDisabledInstrumentation() {
        init(configurationRegistry, List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");

        when(coreConfig.getDisabledInstrumentations())
            .thenReturn(Collections.singletonList("experimental"));
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testExcludedClassesFromInstrumentation() {
        when(coreConfig.getClassesExcludedFromInstrumentation())
            .thenReturn(List.of(WildcardMatcher.valueOf("co.elastic.apm.agent.bci.InstrumentationTest")));
        init(configurationRegistry, List.of(new TestInstrumentation()));
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testExcludedPackageFromInstrumentation() {
        when(coreConfig.getClassesExcludedFromInstrumentation())
            .thenReturn(List.of(WildcardMatcher.valueOf("co.elastic.apm.agent.bci.*")));
        init(configurationRegistry, List.of(new TestInstrumentation()));
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testExcludedDefaultClassesFromInstrumentation() {
        when(coreConfig.getDefaultClassesExcludedFromInstrumentation())
            .thenReturn(List.of(WildcardMatcher.valueOf("co.elastic.apm.agent.bci.InstrumentationTest")));
        init(configurationRegistry, List.of(new TestInstrumentation()));
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testExcludedDefaultPackageFromInstrumentation() {
        when(coreConfig.getDefaultClassesExcludedFromInstrumentation())
            .thenReturn(List.of(WildcardMatcher.valueOf("co.elastic.apm.agent.bci.*")));
        init(configurationRegistry, List.of(new TestInstrumentation()));
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testLegacyDefaultDisabledInstrumentation() {
        init(configurationRegistry, List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");

        when(coreConfig.getDisabledInstrumentations())
            .thenReturn(Collections.singletonList("incubating"));
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testReInitDisableAllInstrumentations() {
        init(configurationRegistry, List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");

        when(coreConfig.isInstrument()).thenReturn(false);
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

    @Test
    void testPatchClassFileVersionJava6ToJava7() {
        // loading classes compiled with bytecode level 50 (Java 6)
        assertThat(StringUtils.startsWithIgnoreCase("APM", "apm")).isTrue();

        // retransforming classes and patch to bytecode level 51 (Java 7)
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new CommonsLangInstrumentation()));

        assertThat(CommonsLangInstrumentation.enterCount).hasValue(0);
        assertThat(CommonsLangInstrumentation.exitCount).hasValue(0);

        assertThat(StringUtils.startsWithIgnoreCase("APM", "apm")).isTrue();

        assertThat(CommonsLangInstrumentation.enterCount).hasPositiveValue();
        assertThat(CommonsLangInstrumentation.exitCount).hasPositiveValue();
    }

    @Test
    void testPatchClassFileVersionJava5ToJava7() {
        // loading classes compiled with bytecode level 49 (Java 6)
        new org.slf4j.event.SubstituteLoggingEvent();

        // retransforming classes and patch to bytecode level 51 (Java 7)
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new LoggerFactoryInstrumentation()));

        assertThat(LoggerFactoryInstrumentation.enterCount).hasValue(0);
        assertThat(LoggerFactoryInstrumentation.exitCount).hasValue(0);

        new org.slf4j.event.SubstituteLoggingEvent();

        assertThat(LoggerFactoryInstrumentation.enterCount).hasPositiveValue();
        assertThat(LoggerFactoryInstrumentation.exitCount).hasPositiveValue();
    }

    @Test
    void testPatchClassFileVersionJava5ToJava7CommonsMath() {
        org.apache.commons.math3.stat.StatUtils.max(new double[]{3.14});

        // retransforming classes and patch to bytecode level 51 (Java 7)
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new StatUtilsInstrumentation()));

        assertThat(StatUtilsInstrumentation.enterCount).hasValue(0);
        assertThat(StatUtilsInstrumentation.exitCount).hasValue(0);

        org.apache.commons.math3.stat.StatUtils.max(new double[]{3.14});

        assertThat(StatUtilsInstrumentation.enterCount).hasPositiveValue();
        assertThat(StatUtilsInstrumentation.exitCount).hasPositiveValue();
    }

    @Test
    void testPrivateConstructorJava7() {
        org.apache.commons.pool2.impl.CallStackUtils.newCallStack("", false, false);

        // retransforming classes and patch to bytecode level 51 (Java 7)
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new CallStackUtilsInstrumentation()));

        assertThat(CallStackUtilsInstrumentation.enterCount).hasValue(0);
        assertThat(CallStackUtilsInstrumentation.exitCount).hasValue(0);

        org.apache.commons.pool2.impl.CallStackUtils.newCallStack("", false, false);

        assertThat(CallStackUtilsInstrumentation.enterCount).hasPositiveValue();
        assertThat(CallStackUtilsInstrumentation.exitCount).hasPositiveValue();
    }

    @Test
    void testPluginClassLoaderGCdAfterUndoingInstrumentation() {
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new ClassLoadingTestInstrumentation()));

        WeakReference<ClassLoader> pluginClassLoader = new WeakReference<>(getPluginClassLoader());
        assertThat(pluginClassLoader.get()).isNotNull();
        assertThat(pluginClassLoader.get()).isInstanceOf(ByteArrayClassLoader.ChildFirst.class);

        ElasticApmAgent.reset();
        assertThat(getPluginClassLoader()).isNull();

        System.gc();
        System.gc();
        await().untilAsserted(() -> assertThat(pluginClassLoader.get()).isNull());
    }

    @Nullable
    public ClassLoader getPluginClassLoader() {
        return null;
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
        @AssignToReturn
        @Advice.OnMethodExit
        public static String onMethodExit() {
            return "intercepted";
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("co.elastic.apm.agent.bci.InstrumentationTest");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("interceptMe");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return List.of("test", "experimental");
        }
    }

    public static class MathInstrumentation extends ElasticApmInstrumentation {
        @AssignToReturn
        @Advice.OnMethodExit(inline = false)
        public static int onMethodExit() {
            return 42;
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("org.apache.commons.math.util.MathUtils");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("sign").and(takesArguments(int.class));
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
            return named(InstrumentationTest.class.getName());
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
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static String onMethodEnter() {
            throw new RuntimeException("This exception should be suppressed");
        }

        @AssignToReturn
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static String onMethodExit(@Advice.Thrown Throwable throwable) {
            throw new RuntimeException("This exception should be suppressed");
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named(InstrumentationTest.class.getName());
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("noExceptionPlease");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.emptyList();
        }
    }

    public static class FieldAccessInstrumentation extends ElasticApmInstrumentation {

        @AssignToField("privateString")
        @Advice.OnMethodEnter
        public static String onEnter(@Advice.Argument(0) String s) {
            return s;
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("co.elastic.apm.agent.bci.InstrumentationTest");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("assignToField");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return List.of("test", "experimental");
        }
    }

    public static class FieldAccessArrayInstrumentation extends ElasticApmInstrumentation {

        @AssignTo(fields = @AssignToField(index = 0, value = "privateString"))
        @Advice.OnMethodEnter
        public static Object[] onEnter(@Advice.Argument(0) String s) {
            return new Object[]{s};
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("co.elastic.apm.agent.bci.InstrumentationTest");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("assignToField");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return List.of("test", "experimental");
        }
    }

    public static class AssignToArgumentInstrumentation extends ElasticApmInstrumentation {

        @AssignToArgument(0)
        @Advice.OnMethodEnter
        public static String onEnter(@Advice.Argument(0) String s) {
            return s + "@AssignToArgument";
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("co.elastic.apm.agent.bci.InstrumentationTest");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("assignToArgument");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return List.of("test", "experimental");
        }
    }

    public static class AssignToArgumentsInstrumentation extends ElasticApmInstrumentation {

        @AssignTo(arguments = {
            @AssignToArgument(index = 0, value = 1),
            @AssignToArgument(index = 1, value = 0)
        })
        @Advice.OnMethodEnter(inline = false)
        public static Object[] onEnter(@Advice.Argument(0) String foo, @Advice.Argument(1) String bar) {
            return new Object[]{foo, bar};
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("co.elastic.apm.agent.bci.InstrumentationTest");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("assignToArguments");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return List.of("test", "experimental");
        }
    }

    public static class AssignToReturnArrayInstrumentation extends ElasticApmInstrumentation {

        @AssignTo(returns = @AssignToReturn(index = 0))
        @Advice.OnMethodExit(inline = false)
        public static Object[] onEnter(@Advice.Argument(0) String foo, @Advice.Argument(1) String bar) {
            return new Object[]{foo + bar};
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("co.elastic.apm.agent.bci.InstrumentationTest");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("assignToReturn");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return List.of("test", "experimental");
        }
    }

    public static class CommonsLangInstrumentation extends ElasticApmInstrumentation {

        public static AtomicInteger enterCount = GlobalVariables.get(CommonsLangInstrumentation.class, "enterCount", new AtomicInteger());
        public static AtomicInteger exitCount = GlobalVariables.get(CommonsLangInstrumentation.class, "exitCount", new AtomicInteger());

        @Advice.OnMethodEnter(inline = false)
        public static void onEnter() {
            enterCount.incrementAndGet();
        }

        @Advice.OnMethodExit(inline = false)
        public static void onExit() {
            exitCount.incrementAndGet();
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return ElementMatchers.nameStartsWith(StringUtils.class.getPackageName());
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return any();
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singletonList("test");
        }

        @Override
        public boolean indyDispatch() {
            return true;
        }
    }

    public static class LoggerFactoryInstrumentation extends ElasticApmInstrumentation {

        public static AtomicInteger enterCount = GlobalVariables.get(CommonsLangInstrumentation.class, "enterCount", new AtomicInteger());
        public static AtomicInteger exitCount = GlobalVariables.get(CommonsLangInstrumentation.class, "exitCount", new AtomicInteger());

        @Advice.OnMethodEnter(inline = false)
        public static void onEnter() {
            enterCount.incrementAndGet();
        }

        @Advice.OnMethodExit(inline = false)
        public static void onExit() {
            exitCount.incrementAndGet();
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named(SubstituteLoggingEvent.class.getName());
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor();
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singletonList("test");
        }

        @Override
        public boolean indyDispatch() {
            return true;
        }
    }

    public static class StatUtilsInstrumentation extends ElasticApmInstrumentation {

        public static AtomicInteger enterCount = GlobalVariables.get(CommonsLangInstrumentation.class, "enterCount", new AtomicInteger());
        public static AtomicInteger exitCount = GlobalVariables.get(CommonsLangInstrumentation.class, "exitCount", new AtomicInteger());

        @Advice.OnMethodEnter(inline = false)
        public static void onEnter() {
            enterCount.incrementAndGet();
        }

        @Advice.OnMethodExit(inline = false)
        public static void onExit() {
            exitCount.incrementAndGet();
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named(StatUtils.class.getName());
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return any();
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singletonList("test");
        }

        @Override
        public boolean indyDispatch() {
            return true;
        }
    }

    public static class CallStackUtilsInstrumentation extends ElasticApmInstrumentation {

        public static AtomicInteger enterCount = GlobalVariables.get(CommonsLangInstrumentation.class, "enterCount", new AtomicInteger());
        public static AtomicInteger exitCount = GlobalVariables.get(CommonsLangInstrumentation.class, "exitCount", new AtomicInteger());

        @Advice.OnMethodEnter(inline = false)
        public static void onEnter() {
            enterCount.incrementAndGet();
        }

        @Advice.OnMethodExit(inline = false)
        public static void onExit() {
            exitCount.incrementAndGet();
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named(CallStackUtils.class.getName());
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return any();
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singletonList("test");
        }

        @Override
        public boolean indyDispatch() {
            return true;
        }
    }

    public static class ClassLoadingTestInstrumentation extends ElasticApmInstrumentation {

        @AssignToReturn
        @Advice.OnMethodExit(inline = false)
        public static ClassLoader onExit() {
            return ClassLoadingTestInstrumentation.class.getClassLoader();
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("co.elastic.apm.agent.bci.InstrumentationTest");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("getPluginClassLoader");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singletonList("test");
        }

        @Override
        public boolean indyDispatch() {
            return true;
        }
    }
}
