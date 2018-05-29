/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.bci;

import co.elastic.apm.bci.bytebuddy.ErrorLoggingListener;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.ServiceLoader;

import static co.elastic.apm.bci.bytebuddy.ClassLoaderNameMatcher.classLoaderWithName;
import static co.elastic.apm.bci.bytebuddy.ClassLoaderNameMatcher.isReflectionClassLoader;
import static net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

public class ElasticApmAgent {

    private static final Logger logger = LoggerFactory.getLogger(ElasticApmAgent.class);
    @Nullable
    private static Instrumentation instrumentation;
    @Nullable
    private static ResettableClassFileTransformer resettableClassFileTransformer;

    /**
     * Allows the installation of this agent via the {@code javaagent} command line argument.
     *
     * @param agentArguments  The unused agent arguments.
     * @param instrumentation The instrumentation instance.
     */
    public static void premain(String agentArguments, Instrumentation instrumentation) {
        initInstrumentation(ElasticApmTracer.builder().build(), instrumentation);
    }

    /**
     * Allows the installation of this agent via the Attach API.
     *
     * @param agentArguments  The unused agent arguments.
     * @param instrumentation The instrumentation instance.
     */
    @SuppressWarnings("unused")
    public static void agentmain(String agentArguments, Instrumentation instrumentation) {
        initInstrumentation(ElasticApmTracer.builder().build(), instrumentation);
    }

    public static void initInstrumentation(ElasticApmTracer tracer, Instrumentation instrumentation) {
        initInstrumentation(tracer, instrumentation, ServiceLoader.load(ElasticApmInstrumentation.class, ElasticApmInstrumentation.class.getClassLoader()));
    }

    public static void initInstrumentation(ElasticApmTracer tracer, Instrumentation instrumentation,
                                           Iterable<ElasticApmInstrumentation> instrumentations) {
        if (ElasticApmAgent.instrumentation != null) {
            logger.warn("Instrumentation has already been initialized");
            return;
        }
        ElasticApmAgent.instrumentation = instrumentation;
        final ByteBuddy byteBuddy = new ByteBuddy()
            .with(TypeValidation.of(logger.isDebugEnabled()))
            .with(MethodGraph.Compiler.ForDeclaredMethods.INSTANCE);
        AgentBuilder agentBuilder = getAgentBuilder(byteBuddy);
        int numberOfAdvices = 0;
        for (final ElasticApmInstrumentation advice : instrumentations) {
            if (isIncluded(advice, tracer.getConfig(CoreConfiguration.class))) {
                numberOfAdvices++;
                agentBuilder = applyAdvice(tracer, agentBuilder, advice);
            }
        }
        logger.debug("Applied {} advices", numberOfAdvices);

        resettableClassFileTransformer = agentBuilder.installOn(ElasticApmAgent.instrumentation);
    }

    private static boolean isIncluded(ElasticApmInstrumentation advice, CoreConfiguration coreConfiguration) {
        final Collection<String> disabledInstrumentations = coreConfiguration.getDisabledInstrumentations();
        return !isGroupDisabled(disabledInstrumentations, advice.getInstrumentationGroupName()) && isInstrumentationEnabled(advice, coreConfiguration);
    }

    private static boolean isGroupDisabled(Collection<String> disabledInstrumentations, String instrumentationGroupName) {
        return disabledInstrumentations.contains(instrumentationGroupName);
    }

    private static boolean isInstrumentationEnabled(ElasticApmInstrumentation advice, CoreConfiguration coreConfiguration) {
        return advice.includeWhenInstrumentationIsDisabled() || coreConfiguration.isInstrument();
    }

    private static AgentBuilder applyAdvice(final ElasticApmTracer tracer, final AgentBuilder agentBuilder,
                                            final ElasticApmInstrumentation advice) {
        logger.debug("Applying advice {}", advice.getClass().getName());
        advice.init(tracer);
        return agentBuilder
            .type(new AgentBuilder.RawMatcher() {
                @Override
                public boolean matches(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, Class<?> classBeingRedefined, ProtectionDomain protectionDomain) {
                    final boolean typeMatches = advice.getTypeMatcher().matches(typeDescription);
                    if (typeMatches) {
                        logger.debug("Type match for advice {}: {} matches {}",
                            advice.getClass().getSimpleName(), advice.getTypeMatcher(), typeDescription);
                    }
                    return typeMatches;
                }
            })
            .transform(new AgentBuilder.Transformer.ForAdvice()
                .advice(new ElementMatcher<MethodDescription>() {
                    @Override
                    public boolean matches(MethodDescription target) {
                        final boolean matches = advice.getMethodMatcher().matches(target);
                        if (matches) {
                            logger.debug("Method match for advice {}: {} matches {}",
                                advice.getClass().getSimpleName(), advice.getMethodMatcher(), target);
                        }
                        return matches;
                    }
                }, advice.getAdviceClass().getName())
                .include(advice.getAdviceClass().getClassLoader())
                .withExceptionHandler(PRINTING))
            .asDecorator();
    }

    /**
     * Reverts instrumentation of classes and re-transforms them to their state without the agent.
     * <p>
     * This is only to be used for unit tests
     * </p>
     */
    public static synchronized void reset() {
        if (resettableClassFileTransformer == null || instrumentation == null) {
            throw new IllegalStateException("Reset was called before init");
        }
        resettableClassFileTransformer.reset(instrumentation, AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        instrumentation = null;
        resettableClassFileTransformer = null;
    }

    private static AgentBuilder getAgentBuilder(ByteBuddy byteBuddy) {
        return new AgentBuilder.Default(byteBuddy)
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new ErrorLoggingListener())
            .ignore(any(), isReflectionClassLoader())
            .or(any(), classLoaderWithName("org.codehaus.groovy.runtime.callsite.CallSiteClassLoader"))
            .or(nameStartsWith("java."))
            .or(nameStartsWith("com.sun."))
            .or(nameStartsWith("sun"))
            .or(nameStartsWith("org.aspectj."))
            .or(nameStartsWith("org.groovy."))
            .or(nameStartsWith("com.p6spy."))
            .or(nameStartsWith("net.bytebuddy."))
            .or(nameContains("javassist"))
            .or(nameContains(".asm."))
            .disableClassFormatChanges();
    }

}
