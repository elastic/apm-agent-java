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
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import co.elastic.apm.agent.sdk.state.GlobalThreadLocal;
import net.bytebuddy.asm.Advice;

/**
 * The constructor can optionally have a {@link ElasticApmTracer} parameter.
 */
public abstract class TracerAwareInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static final Tracer tracer = GlobalTracer.get();

    /**
     * When this method returns {@code true} the whole package (starting at the {@linkplain #getAdviceClass() advice's} package)
     * will be loaded from a plugin class loader that has both the agent class loader and the class loader of the class this instruments as
     * parents.
     * <p>
     * This instructs Byte Buddy to dispatch to the advice methods via an {@code INVOKEDYNAMIC} instruction.
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
     *         that argument also has to be of type {@link Object} and you have to cast it within the method body.
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
     *         Due to the automatic plugin classloader creation that is based on package scanning,
     *         plugins need be in their own uniquely named package.
     *         As the package of the {@link #getAdviceClass()} is used as the root,
     *         all advices have to be at the top level of the plugin.
     *     </li>
     * </ul>
     *
     * @return whether to load the classes of this plugin in dedicated plugin class loaders (one for each unique class loader)
     * and dispatch to the {@linkplain #getAdviceClass() advice} via an {@code INVOKEDYNAMIC} instruction.
     */
    public boolean indyPlugin() {
        return false;
    }

}
