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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.security.ProtectionDomain;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.any;

/**
 * An advice is responsible for instrumenting methods (see {@link #getMethodMatcher()}) in particular classes
 * (see {@link #getTypeMatcher()}).
 * <p>
 * The actual instrumentation of the matched methods is performed by static methods within this class,
 * which are annotated by {@link net.bytebuddy.asm.Advice.OnMethodEnter} or {@link net.bytebuddy.asm.Advice.OnMethodExit}.
 * </p>
 * <p>
 * The constructor can optionally have a {@link ElasticApmTracer} parameter.
 * </p>
 */
public abstract class ElasticApmInstrumentation {

    @Nullable
    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    /**
     * Initializes the advice with the {@link ElasticApmTracer}
     * <p>
     * This enables tests to register a custom instance with a {@link co.elastic.apm.agent.impl.ElasticApmTracerBuilder#configurationRegistry}
     * and {@link co.elastic.apm.agent.impl.ElasticApmTracerBuilder#reporter} which is specific to a particular test or test class.
     * </p>
     *
     * @param tracer the tracer to use for this advice.
     */
    static void staticInit(ElasticApmTracer tracer) {
        // allow re-init with a different tracer
        ElasticApmInstrumentation.tracer = tracer;
    }

    @Nullable
    @VisibleForAdvice
    public static AbstractSpan<?> getActive() {
        if (tracer != null) {
            return tracer.getActive();
        }
        return null;
    }

    @Nullable
    @VisibleForAdvice
    public static Span getActiveSpan() {
        if (tracer != null) {
            final AbstractSpan<?> active = tracer.getActive();
            if (active instanceof Span) {
                return (Span) active;
            }
        }
        return null;
    }


    @Nullable
    @VisibleForAdvice
    public static Span getActiveExitSpan() {
        final Span span = getActiveSpan();
        if (span != null && span.isExit()) {
            return span;
        }
        return null;
    }

    @Nullable
    @VisibleForAdvice
    public static Span createExitSpan() {
        final AbstractSpan<?> activeSpan = getActive();
        if (activeSpan == null || activeSpan.isExit()) {
            return null;
        }

       return activeSpan.createExitSpan();
    }

    /**
     * Pre-select candidates solely based on the class name for the slower {@link #getTypeMatcher()},
     * at the expense of potential false negative matches.
     * <p>
     * Any matcher which does not only take the class name into account,
     * causes the class' bytecode to be parsed.
     * If the matcher needs information from other classes than the one currently being loaded,
     * like it's super class,
     * those classes have to be loaded from the file system,
     * unless they are cached or already loaded.
     * </p>
     */
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return any();
    }

    /**
     * Post filters classes that pass the {@link #getTypeMatcher()} by version.
     */
    public ElementMatcher.Junction<ProtectionDomain> getImplementationVersionPostFilter() {
        return any();
    }

    /**
     * The type matcher selects types which should be instrumented by this advice
     * <p>
     * To make type matching more efficient,
     * first apply the cheaper matchers like {@link ElementMatchers#nameStartsWith(String)} and {@link ElementMatchers#isInterface()}
     * which pre-select the types as narrow as possible.
     * Only then use more expensive matchers like {@link ElementMatchers#hasSuperType(ElementMatcher)}
     * </p>
     *
     * @return the type matcher
     */
    public abstract ElementMatcher<? super TypeDescription> getTypeMatcher();

    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return any();
    }

    /**
     * The method matcher selects methods of types matching {@link #getTypeMatcher()},
     * which should be instrumented
     *
     * @return the method matcher
     */
    public abstract ElementMatcher<? super MethodDescription> getMethodMatcher();

    public Class<?> getAdviceClass() {
        return getClass();
    }


    /**
     * Return {@code true},
     * if this instrumentation should even be applied when
     * {@link co.elastic.apm.agent.configuration.CoreConfiguration#instrument} is set to {@code false}.
     */
    public boolean includeWhenInstrumentationIsDisabled() {
        return false;
    }

    /**
     * Returns a name which groups several instrumentations into a logical group.
     * <p>
     * This name is used in {@link co.elastic.apm.agent.configuration.CoreConfiguration#disabledInstrumentations} to exclude a logical group
     * of instrumentations.
     * </p>
     *
     * @return a name which groups several instrumentations into a logical group
     */
    public abstract Collection<String> getInstrumentationGroupNames();

    @Nullable
    public Advice.OffsetMapping.Factory<?> getOffsetMapping() {
        return null;
    }

    public void onTypeMatch(TypeDescription typeDescription, ClassLoader classLoader, ProtectionDomain protectionDomain, @Nullable Class<?> classBeingRedefined) {
    }

    /**
     * When this method returns {@code true} the whole package (starting at the {@linkplain #getAdviceClass() advice's} package)
     * will be loaded from a plugin class loader that has both the agent class loader and the class loader of the class this instruments as
     * parents.
     * <p>
     * This instructs Byte Buddy to dispatch to the advice methods via an {@code INVOKEDYNAMIC} instruction.
     * Upon first invocation of an instrumented method,
     * this will call {@link IndyBootstrap#bootstrap} to determine the target {@link java.lang.invoke.ConstantCallSite}.
     * </p>
     * <p>
     * Things to watch out for when using indy plugins:
     * </p>
     * <ul>
     *     <li>
     *         Set {@link Advice.OnMethodEnter#inline()} and {@link Advice.OnMethodExit#inline()} to {@code false} on all advices.
     *         As the {@code readOnly} flag in Byte Buddy annotations such as {@link Advice.Return#readOnly()} cannot be used with non
     *         {@linkplain Advice.OnMethodEnter#inline() inlined advices},
     *         use {@link co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo} and friends.
     *     </li>
     *     <li>
     *         Both the return type and the arguments of advice methods must not contain types from the agent.
     *         If you'd like to return a {@link Span} from an advice, for example, return an {@link Object} instead.
     *         When using an {@link net.bytebuddy.asm.Advice.Enter} argument on the
     *         {@linkplain net.bytebuddy.asm.Advice.OnMethodExit exit advice},
     *         that argument also has to be of type {@link Object} and you have to cast it within the method body.
     *         The reason is that the return value will become a local variable in the instrumented method.
     *         Due to OSGi, those methods may not have access to agent types.
     *         Another case is when the instrumented class is inside the bootstrap classloader.
     *     </li>
     *     <li>
     *         When an advice instruments classes in multiple class loaders, the plugin classes will be loaded form multiple class loaders.
     *         In order to still share state across those plugin class loaders, use {@link co.elastic.apm.agent.util.GlobalVariables} or {@link GlobalState}.
     *         That's necessary as static variables are scoped to the class loader they are defined in.
     *     </li>
     *     <li>
     *         Don't use {@link ThreadLocal}s as it can lead to class loader leaks.
     *         Use {@link co.elastic.apm.agent.threadlocal.RemoveOnGetThreadLocal} instead.
     *     </li>
     *     <li>
     *         Due to the automatic plugin classloader creation that is based on package scanning,
     *         plugins need be in their own uniquely named package.
     *         As the package of the {@link #getAdviceClass()} is used as the root,
     *         all advices have to be at the top level of the plugin.
     *     </li>
     * </ul>
     *
     * @return whether to load the classes of this plugin in dedicated plugin class loaders (one for each unique class loader)
     * and dispatch to the {@linkplain #getAdviceClass() advice} via an {@code INVOKEDYNAMIC} instruction.
     * @see IndyBootstrap
     */
    public boolean indyPlugin() {
        return false;
    }
}
