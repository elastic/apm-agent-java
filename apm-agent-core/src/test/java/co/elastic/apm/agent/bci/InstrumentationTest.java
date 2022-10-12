/*
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
 */
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.bytebuddy.Instrumented;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.state.GlobalVariables;
import co.elastic.apm.agent.util.ExecutorUtils;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
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
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.slf4j.event.SubstituteLoggingEvent;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static co.elastic.apm.agent.util.MockitoMatchers.containsValue;
import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyCollection;
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
        doReturn(false).when(coreConfig).isInstrumentationEnabled(containsValue("test"));
        init(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testEnsureInstrumented() {
        init(List.of());
        assertThat(interceptMe()).isEmpty();
        DynamicTransformer.ensureInstrumented(getClass(), List.of(TestInstrumentation.class));
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testConcurrentEnsureInstrumented() throws InterruptedException {
        init(List.of());
        assertThat(interceptMe()).isEmpty();
        TestCounterInstrumentation.resetCounter();
        int nThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        for (int i = 0; i < nThreads; i++) {
            executorService.submit(() -> DynamicTransformer.ensureInstrumented(getClass(), List.of(TestCounterInstrumentation.class)));
        }
        ExecutorUtils.shutdownAndWaitTermination(executorService);
        assertThat(TestCounterInstrumentation.getCounter()).isEqualTo(1);
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testReInitEnableOneInstrumentation() {
        doReturn(false).when(coreConfig).isInstrumentationEnabled(containsValue("test"));
        init(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEmpty();

        doReturn(true).when(coreConfig).isInstrumentationEnabled(anyCollection());
        ElasticApmAgent.doReInitInstrumentation(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testDefaultDisabledInstrumentation() {
        init(List.of(new TestInstrumentation()));
        assertThat(interceptMe()).isEqualTo("intercepted");

        doReturn(false).when(coreConfig).isInstrumentationEnabled(containsValue("experimental"));
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
    void testWarmup() {
        doReturn(true).when(coreConfig).shouldWarmupByteBuddy();
        assertThat(Instrumented.isWarmedUp()).isFalse();
        Instrumented instrumented = new Instrumented();
        assertThat(instrumented.isInstrumented()).isFalse();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
        assertThat(Instrumented.isWarmedUp()).isTrue();
        assertThat(instrumented.isInstrumented()).isTrue();
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

        assertThat(CommonsLangInstrumentation.AdviceClass.enterCount).hasValue(0);
        assertThat(CommonsLangInstrumentation.AdviceClass.exitCount).hasValue(0);

        assertThat(StringUtils.startsWithIgnoreCase("APM", "apm")).isTrue();

        assertThat(CommonsLangInstrumentation.AdviceClass.enterCount).hasPositiveValue();
        assertThat(CommonsLangInstrumentation.AdviceClass.exitCount).hasPositiveValue();
    }

    @Test
    @DisabledOnJre(JRE.JAVA_15) // https://github.com/elastic/apm-agent-java/issues/1944
    void testPatchClassFileVersionJava5ToJava7() {
        // loading classes compiled with bytecode level 49 (Java 6)
        new org.slf4j.event.SubstituteLoggingEvent();

        // retransforming classes and patch to bytecode level 51 (Java 7)
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new LoggerFactoryInstrumentation()));

        assertThat(LoggerFactoryInstrumentation.AdviceClass.enterCount).hasValue(0);
        assertThat(LoggerFactoryInstrumentation.AdviceClass.exitCount).hasValue(0);

        new org.slf4j.event.SubstituteLoggingEvent();

        assertThat(LoggerFactoryInstrumentation.AdviceClass.enterCount).hasPositiveValue();
        assertThat(LoggerFactoryInstrumentation.AdviceClass.exitCount).hasPositiveValue();
    }

    @Test
    @DisabledOnJre(JRE.JAVA_15) // https://github.com/elastic/apm-agent-java/issues/1944
    void testPatchClassFileVersionJava5ToJava7CommonsMath() {
        org.apache.commons.math3.stat.StatUtils.max(new double[]{3.14});

        // retransforming classes and patch to bytecode level 51 (Java 7)
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new StatUtilsInstrumentation()));

        assertThat(StatUtilsInstrumentation.AdviceClass.enterCount).hasValue(0);
        assertThat(StatUtilsInstrumentation.AdviceClass.exitCount).hasValue(0);

        org.apache.commons.math3.stat.StatUtils.max(new double[]{3.14});

        assertThat(StatUtilsInstrumentation.AdviceClass.enterCount).hasPositiveValue();
        assertThat(StatUtilsInstrumentation.AdviceClass.exitCount).hasPositiveValue();
    }

    @Test
    @DisabledOnJre(JRE.JAVA_15) // https://github.com/elastic/apm-agent-java/issues/1944
    void testPatchClassFileVersionJava4ToJava7CommonsMath() {
        org.apache.log4j.LogManager.exists("not");

        // retransforming classes and patch to bytecode level 51 (Java 7)
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new LogManagerInstrumentation()));

        assertThat(LogManagerInstrumentation.AdviceClass.enterCount).hasValue(0);
        assertThat(LogManagerInstrumentation.AdviceClass.exitCount).hasValue(0);

        org.apache.log4j.LogManager.exists("not");

        assertThat(LogManagerInstrumentation.AdviceClass.enterCount).hasPositiveValue();
        assertThat(LogManagerInstrumentation.AdviceClass.exitCount).hasPositiveValue();
    }

    @Test
    void testPrivateConstructorJava7() {
        org.apache.commons.pool2.impl.CallStackUtils.newCallStack("", false, false);

        // retransforming classes and patch to bytecode level 51 (Java 7)
        ElasticApmAgent.initInstrumentation(tracer,
            ByteBuddyAgent.install(),
            Collections.singletonList(new CallStackUtilsInstrumentation()));

        assertThat(CallStackUtilsInstrumentation.AdviceClass.enterCount).hasValue(0);
        assertThat(CallStackUtilsInstrumentation.AdviceClass.exitCount).hasValue(0);

        org.apache.commons.pool2.impl.CallStackUtils.newCallStack("", false, false);

        assertThat(CallStackUtilsInstrumentation.AdviceClass.enterCount).hasPositiveValue();
        assertThat(CallStackUtilsInstrumentation.AdviceClass.exitCount).hasPositiveValue();
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
        assertThatThrownBy(() -> ElasticApmAgent.validateAdvice(new InlinedIndyAdviceInstrumentation()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testAdviceWithAgentReturnType() {
        assertThatThrownBy(() -> ElasticApmAgent.validateAdvice(new AgentTypeReturnInstrumentation()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testAdviceWithAgentParameterType() {
        assertThatThrownBy(() -> ElasticApmAgent.validateAdvice(new AgentTypeParameterInstrumentation()))
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
        public static class AdviceClass {
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(inline = false)
            public static String onMethodExit() {
                return "intercepted";
            }
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

    public static class TestCounterInstrumentation extends TestInstrumentation {

        private static final AtomicInteger counter = new AtomicInteger();

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.bci.InstrumentationTest$TestInstrumentation$AdviceClass";
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            counter.incrementAndGet();
            return super.getTypeMatcher();
        }

        public static void resetCounter() {
            counter.set(0);
        }

        public static int getCounter() {
            return counter.get();
        }
    }

    public static class MathInstrumentation extends TracerAwareInstrumentation {
        public static class AdviceClass {
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(inline = false)
            public static int onMethodExit() {
                return 42;
            }
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

    public static class ExceptionInstrumentation extends TracerAwareInstrumentation {
        public static class AdviceClass {
            @Advice.OnMethodExit(inline = false)
            public static void onMethodExit() {
                throw new RuntimeException("This exception should not be suppressed");
            }
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

    public static class SuppressExceptionInstrumentation extends TracerAwareInstrumentation {
        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static String onMethodEnter() {
                throw new RuntimeException("This exception should be suppressed");
            }

            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static String onMethodExit(@Advice.Thrown Throwable throwable) {
                throw new RuntimeException("This exception should be suppressed");
            }
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

    public static class FieldAccessInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
            @Advice.AssignReturned.ToFields(@ToField(value = "privateString", typing = DYNAMIC))
            @Advice.OnMethodEnter(inline = false)
            public static String onEnter(@Advice.Argument(0) String s) {
                return s;
            }
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

    public static class FieldAccessArrayInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
            @Advice.AssignReturned.ToFields(@ToField(index = 0, value = "privateString", typing = DYNAMIC))
            @Advice.OnMethodEnter(inline = false)
            public static Object[] onEnter(@Advice.Argument(0) String s) {
                return new Object[]{s};
            }
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

    public static class AssignToArgumentInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
            @Advice.AssignReturned.ToArguments(@ToArgument(0))
            @Advice.OnMethodEnter(inline = false)
            public static String onEnter(@Advice.Argument(0) String s) {
                return s + "@AssignToArgument";
            }
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

    public static class AssignToArgumentsInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
            @Advice.AssignReturned.ToArguments({
                @ToArgument(index = 0, value = 1, typing = DYNAMIC),
                @ToArgument(index = 1, value = 0, typing = DYNAMIC)
            })
            @Advice.OnMethodEnter(inline = false)
            public static Object[] onEnter(@Advice.Argument(0) String foo, @Advice.Argument(1) String bar) {
                return new Object[]{foo, bar};
            }
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

    public static class AssignToReturnArrayInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
            @Advice.AssignReturned.ToReturned(index = 0, typing = DYNAMIC)
            @Advice.OnMethodExit(inline = false)
            public static Object[] onEnter(@Advice.Argument(0) String foo, @Advice.Argument(1) String bar) {
                return new Object[]{foo + bar};
            }
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

    public static class CommonsLangInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
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

    public static class LoggerFactoryInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
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

    public static class StatUtilsInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
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

    public static class LogManagerInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
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

    public static class CallStackUtilsInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
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

    public static class ClassLoadingTestInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(inline = false)
            public static ClassLoader onExit() {
                return ClassLoadingTestInstrumentation.class.getClassLoader();
            }
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

    public static class InlinedIndyAdviceInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
            @Advice.OnMethodEnter
            public static void onExit() {
            }
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

    public static class AgentTypeReturnInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
            @Advice.OnMethodEnter(inline = false)
            public static Span onEnter() {
                return null;
            }
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

    public static class AgentTypeParameterInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
            @Advice.OnMethodEnter(inline = false)
            public static Object onEnter() {
                return null;
            }

            @Advice.OnMethodExit(inline = false)
            private static void onExit(@Advice.Enter Span span) {
            }
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

    public static class GetClassLoaderInstrumentation extends TracerAwareInstrumentation {

        public static class AdviceClass {
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(inline = false)
            public static ClassLoader onExit(@Advice.Origin Class<?> clazz) {
                return clazz.getClassLoader();
            }
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
