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
package co.elastic.apm.agent.sdk;

import co.elastic.apm.agent.sdk.advice.AssignTo;
import co.elastic.apm.agent.sdk.state.GlobalThreadLocal;
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
 * For internal plugins, the whole package (starting at {@code co.elastic.apm.agent.<plugin-root>})
 * will be loaded from a plugin class loader that has both the agent class loader and the class loader of the
 * instrumented class as parents.
 * This class loader is also known as the {@code IndyPluginClassLoader}.
 * For external plugins, the whole jar will be loaded from the indy plugin class loader.
 * <p>
 * The advice methods will be dispatched via an {@code INVOKEDYNAMIC} instruction.
 * Upon first invocation of an instrumented method,
 * this will call {@code IndyBootstrap#bootstrap} to determine the target {@link java.lang.invoke.ConstantCallSite}.
 * </p>
 * <p>
 * Things to watch out for when using indy plugins:
 * </p>
 * <ul>
 *     <li>
 *         Set {@link Advice.OnMethodEnter#inline()} and {@link Advice.OnMethodExit#inline()} to {@code false} on all advices.
 *         As the {@code readOnly} flag in Byte Buddy annotations such as {@link Advice.Return#readOnly()} cannot be used with non
 *         {@linkplain Advice.OnMethodEnter#inline() inlined advices},
 *         use {@link AssignTo} and friends.
 *     </li>
 *     <li>
 *         Both the return type and the arguments of advice methods must not contain types from the agent.
 *         If you'd like to return a span from an advice, for example, return an {@link Object} instead.
 *         When using an {@link Advice.Enter} argument on the {@linkplain Advice.OnMethodExit exit advice},
 *         that argument also must not be an agent type.
 *         Use {@link Object} and cast within the method body instead.
 *         The reason is that the return value will become a local variable in the instrumented method.
 *         Due to OSGi, those methods may not have access to agent types.
 *         Another case is when the instrumented class is inside the bootstrap classloader.
 *     </li>
 *     <li>
 *         When an advice instruments classes in multiple class loaders, the plugin classes will be loaded form multiple class loaders.
 *         In order to still share state across those plugin class loaders,
 *         use {@link co.elastic.apm.agent.sdk.state.GlobalVariables} or {@link co.elastic.apm.agent.sdk.state.GlobalState}.
 *         That's necessary as static variables are scoped to the class loader they are defined in.
 *     </li>
 *     <li>
 *         Don't use {@link ThreadLocal}s as it can lead to class loader leaks.
 *         Use {@link GlobalThreadLocal} instead.
 *     </li>
 *     <li>
 *         This applies to internal plugins only:
 *         Due to the automatic plugin classloader creation that is based on package scanning,
 *         plugins need to be in their own uniquely named package.
 *     </li>
 * </ul>
 *
 */
public abstract class ElasticApmInstrumentation {
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
     * Post filters classes that pass the {@link #getTypeMatcher()} by {@link ProtectionDomain}.
     */
    public ElementMatcher.Junction<ProtectionDomain> getProtectionDomainPostFilter() {
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

    /**
     * Implementing the advice and instrumentation at the same class is <b>disallowed</b> and will throw a validation error when trying to do so.
     * They are loaded in different contexts with different purposes. The instrumentation class is loaded by the agent class
     * loader, whereas the advice class needs to be loaded by a class loader that has visibility to the instrumented
     * type and library, as well as the agent classes. Therefore, loading the advice class through the agent class
     * loader may cause linkage-related errors.
     * <p>
     *     ANY INSTRUMENTATION THAT OVERRIDES THIS METHOD MUST NOT CAUSE THE LOADING OF THE ADVICE CLASS.
     *     For example, implementing it as {@code MyAdvice.class.getName()} is not allowed.
     * </p>
     * @return the name of the advice class corresponding this instrumentation
     */
    public String getAdviceClassName() {
        return getClass().getName() + "$AdviceClass";
    }

    /**
     * Returns {@code true} if this instrumentation should be applied even when {@code instrument} is set to {@code false}.
     */
    public boolean includeWhenInstrumentationIsDisabled() {
        return false;
    }

    /**
     * Returns a name which groups several instrumentations into a logical group.
     * <p>
     * This name is used in {@code disabled_instrumentations} to exclude a logical group
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

}
