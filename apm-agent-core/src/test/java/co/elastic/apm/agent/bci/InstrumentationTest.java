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
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import co.elastic.apm.agent.sdk.state.GlobalVariables;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;

class InstrumentationTest {

    private ElasticApmTracer tracer;
    private ConfigurationRegistry configurationRegistry;
    private CoreConfiguration coreConfig;
    private String privateString;

    @BeforeEach
    void setup() {
        tracer = MockTracer.createRealTracer();
        configurationRegistry = tracer.getConfigurationRegistry();
        coreConfig = configurationRegistry.getConfig(CoreConfiguration.class);
    }

    @AfterEach
    void reset() {
        ElasticApmAgent.reset();
    }

    @Test
    void testIntercept() {
        init(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testFieldAccess() {
        init(List.of(new FieldAccessInstrumentation()));
        assignToField("@AssignToField");
        assertThat(privateString).isEqualTo("@AssignToField");
    }

    @Test
    void testFieldAccessArray() {
        init(List.of(new FieldAccessArrayInstrumentation()));
        assignToField("@AssignToField");
        assertThat(privateString).isEqualTo("@AssignToField");
    }

    public void assignToField(String s) {
    }

    @Test
    void testAssignToArgument() {
        init(List.of(new AssignToArgumentInstrumentation()));
        assertThat(assignToArgument("foo")).isEqualTo("foo@AssignToArgument");
    }

    public String assignToArgument(String s) {
        return s;
    }

    @Test
    void testAssignToArgumentArray() {
        init(List.of(new AssignToArgumentsInstrumentation()));
        assertThat(assignToArguments("foo", "bar")).isEqualTo("barfoo");
    }

    public String assignToArguments(String foo, String bar) {
        return foo + bar;
    }

    @Test
    void testAssignToReturnArray() {
        init(List.of(new AssignToReturnArrayInstrumentation()));
        assertThat(assignToReturn("foo", "bar")).isEqualTo("foobar");
    }

    @Nullable
    public String assignToReturn(String foo, String bar) {
        return null;
    }

    @Test
    void testDisabled() {
        doReturn(Collections.singletonList("test")).when(coreConfig).getDisabledInstrumentations();
        init(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testEnsureInstrumented() {
        init(List.of());
        assertThat(interceptMe()).isEmpty();
        DynamicTransformer.Accessor.get().ensureInstrumented(getClass(), List.of(TestInstrumentation.class));
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testReInitEnableOneInstrumentation() {
        doReturn(Collections.singletonList("test")).when(coreConfig).getDisabledInstrumentations();
        init(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();

        doReturn(List.of()).when(coreConfig).getDisabledInstrumentations();
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testDefaultDisabledInstrumentation() {
        init(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");

        doReturn(Collections.singletonList("experimental")).when(coreConfig).getDisabledInstrumentations();
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testExcludedClassesFromInstrumentation() {
        doReturn(List.of(WildcardMatcher.valueOf("co.elastic.apm.agent.bci.InstrumentationTest")))
            .when(coreConfig).getClassesExcludedFromInstrumentation();
        init(List.of(new TestInstrumentation()));
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testExcludedPackageFromInstrumentation() {
        doReturn(List.of(WildcardMatcher.valueOf("co.elastic.apm.agent.bci.*")))
            .when(coreConfig).getClassesExcludedFromInstrumentation();
        init(List.of(new TestInstrumentation()));
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testExcludedDefaultClassesFromInstrumentation() {
        doReturn(List.of(WildcardMatcher.valueOf("co.elastic.apm.agent.bci.InstrumentationTest")))
            .when(coreConfig).getDefaultClassesExcludedFromInstrumentation();
        init(List.of(new TestInstrumentation()));
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testExcludedDefaultPackageFromInstrumentation() {
        doReturn(List.of(WildcardMatcher.valueOf("co.elastic.apm.agent.bci.*")))
            .when(coreConfig).getDefaultClassesExcludedFromInstrumentation();
        init(List.of(new TestInstrumentation()));
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testLegacyDefaultDisabledInstrumentation() {
        init(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");

        doReturn(Collections.singletonList("incubating")).when(coreConfig).getDisabledInstrumentations();
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testReInitDisableAllInstrumentations() {
        init(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");

        doReturn(false).when(coreConfig).isInstrument();
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
        // so we we are better off not touching Java 1.3 code (like org.apache.commons.math.util.MathUtils) at all
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
    void testPatchClassFileVersionJava4ToJava7CommonsMath() {
        org.apache.log4j.LogManager.exists("not");

        // retransforming classes and patch to bytecode level 51 (Java 7)
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new LogManagerInstrumentation()));

        assertThat(LogManagerInstrumentation.enterCount).hasValue(0);
        assertThat(LogManagerInstrumentation.exitCount).hasValue(0);

        org.apache.log4j.LogManager.exists("not");

        assertThat(LogManagerInstrumentation.enterCount).hasPositiveValue();
        assertThat(LogManagerInstrumentation.exitCount).hasPositiveValue();
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

    @Test
    void testNoClassLoaderLeakWhenInstrumentedApplicationIsUndeployed() throws Exception {
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new GetClassLoaderInstrumentation()));

        Map<String, byte[]> typeDefinitions = Map.of(
            InstrumentedInIsolatedClassLoader.class.getName(),
            ClassFileLocator.ForClassLoader.of(ClassLoader.getSystemClassLoader()).locate(InstrumentedInIsolatedClassLoader.class.getName()).resolve()
        );
        ClassLoader applicationCL = new ByteArrayClassLoader.ChildFirst(null, true, typeDefinitions, ByteArrayClassLoader.PersistenceHandler.MANIFEST);
        Class<?> instrumentedClass = applicationCL.loadClass(InstrumentedInIsolatedClassLoader.class.getName());
        assertThat(instrumentedClass.getMethod("getClassLoader").invoke(null)).isSameAs(applicationCL);

        WeakReference<ClassLoader> applicationCLRef = new WeakReference<>(applicationCL);
        // after clearing these references, the application class loader is expected to be eligible for GC
        // the agent must not hold strong references the instrumented class or it's class loader
        applicationCL = null;
        instrumentedClass = null;

        System.gc();
        System.gc();
        await().untilAsserted(() -> assertThat(applicationCLRef.get()).isNull());
    }

    public static class InstrumentedInIsolatedClassLoader {

        @Nullable
        public static ClassLoader getClassLoader() {
            return null;
        }

    }

    @Test
    void testInlinedIndyAdvice() {
        assertThatThrownBy(() -> ElasticApmAgent.validateAdvice(InlinedIndyAdviceInstrumentation.class))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testAdviceWithAgentReturnType() {
        assertThatThrownBy(() -> ElasticApmAgent.validateAdvice(AgentTypeReturnInstrumentation.class))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testAdviceWithAgentParameterType() {
        assertThatThrownBy(() -> ElasticApmAgent.validateAdvice(AgentTypeParameterInstrumentation.class))
            .isInstanceOf(IllegalStateException.class);
    }

    @Nullable
    public AbstractSpan<?> getSpanFromThreadLocal() {
        return null;
    }

    @Nullable
    public ClassLoader getPluginClassLoader() {
        return null;
    }

    private void init(List<ElasticApmInstrumentation> instrumentations) {
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(), instrumentations);
    }

    private String interceptMe() {
        return "";
    }

    public static class TestInstrumentation extends TracerAwareInstrumentation {
        @AssignTo.Return
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

        @Override
        public boolean indyPlugin() {
            return false;
        }
    }

    public static class MathInstrumentation extends TracerAwareInstrumentation {
        @AssignTo.Return
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

        @Override
        public boolean indyPlugin() {
            return false;
        }
    }

    public static class ExceptionInstrumentation extends TracerAwareInstrumentation {
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

        @Override
        public boolean indyPlugin() {
            return false;
        }
    }

    public static class SuppressExceptionInstrumentation extends TracerAwareInstrumentation {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static String onMethodEnter() {
            throw new RuntimeException("This exception should be suppressed");
        }

        @AssignTo.Return
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

        @Override
        public boolean indyPlugin() {
            return false;
        }
    }

    public static class FieldAccessInstrumentation extends TracerAwareInstrumentation {

        @AssignTo.Field("privateString")
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

        @Override
        public boolean indyPlugin() {
            return false;
        }
    }

    public static class FieldAccessArrayInstrumentation extends TracerAwareInstrumentation {

        @AssignTo(fields = @AssignTo.Field(index = 0, value = "privateString"))
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

        @Override
        public boolean indyPlugin() {
            return false;
        }
    }

    public static class AssignToArgumentInstrumentation extends TracerAwareInstrumentation {

        @AssignTo.Argument(0)
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

        @Override
        public boolean indyPlugin() {
            return false;
        }
    }

    public static class AssignToArgumentsInstrumentation extends TracerAwareInstrumentation {

        @AssignTo(arguments = {
            @AssignTo.Argument(index = 0, value = 1),
            @AssignTo.Argument(index = 1, value = 0)
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

        @Override
        public boolean indyPlugin() {
            return false;
        }
    }

    public static class AssignToReturnArrayInstrumentation extends TracerAwareInstrumentation {

        @AssignTo(returns = @AssignTo.Return(index = 0))
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

        @Override
        public boolean indyPlugin() {
            return false;
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

    }

    public static class LoggerFactoryInstrumentation extends ElasticApmInstrumentation {

        public static AtomicInteger enterCount = GlobalVariables.get(LoggerFactoryInstrumentation.class, "enterCount", new AtomicInteger());
        public static AtomicInteger exitCount = GlobalVariables.get(LoggerFactoryInstrumentation.class, "exitCount", new AtomicInteger());

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

    }

    public static class StatUtilsInstrumentation extends ElasticApmInstrumentation {

        public static AtomicInteger enterCount = GlobalVariables.get(StatUtilsInstrumentation.class, "enterCount", new AtomicInteger());
        public static AtomicInteger exitCount = GlobalVariables.get(StatUtilsInstrumentation.class, "exitCount", new AtomicInteger());

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

    }

    public static class LogManagerInstrumentation extends ElasticApmInstrumentation {

        public static AtomicInteger enterCount = GlobalVariables.get(LogManagerInstrumentation.class, "enterCount", new AtomicInteger());
        public static AtomicInteger exitCount = GlobalVariables.get(LogManagerInstrumentation.class, "exitCount", new AtomicInteger());

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
            return named("org.apache.log4j.LogManager");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return any();
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singletonList("test");
        }

    }

    public static class CallStackUtilsInstrumentation extends ElasticApmInstrumentation {

        public static AtomicInteger enterCount = GlobalVariables.get(CallStackUtilsInstrumentation.class, "enterCount", new AtomicInteger());
        public static AtomicInteger exitCount = GlobalVariables.get(CallStackUtilsInstrumentation.class, "exitCount", new AtomicInteger());

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

    }

    public static class ClassLoadingTestInstrumentation extends ElasticApmInstrumentation {

        @AssignTo.Return
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

    }

    public static class InlinedIndyAdviceInstrumentation extends ElasticApmInstrumentation {

        @Advice.OnMethodEnter
        public static void onExit() {
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return none();
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return none();
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singletonList("test");
        }

    }

    public static class AgentTypeReturnInstrumentation extends ElasticApmInstrumentation {

        @Advice.OnMethodEnter(inline = false)
        public static Span onEnter() {
            return null;
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return none();
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return none();
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singletonList("test");
        }

    }

    public static class AgentTypeParameterInstrumentation extends ElasticApmInstrumentation {

        @Advice.OnMethodEnter(inline = false)
        public static Object onEnter() {
            return null;
        }

        @Advice.OnMethodExit(inline = false)
        private static void onExit(@Advice.Enter Span span) {
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return none();
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return none();
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singletonList("test");
        }

    }

    public static class GetClassLoaderInstrumentation extends ElasticApmInstrumentation {

        @AssignTo.Return
        @Advice.OnMethodExit(inline = false)
        public static ClassLoader onExit(@Advice.Origin Class<?> clazz) {
            return clazz.getClassLoader();
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named(InstrumentedInIsolatedClassLoader.class.getName());
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("getClassLoader");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singletonList("test");
        }

    }
}
