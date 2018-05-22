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

import co.elastic.apm.impl.ElasticApmTracer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;

/**
 * An advice is responsible for instrumenting methods (see {@link #getMethodMatcher()}) in particular classes
 * (see {@link #getTypeMatcher()}).
 * <p>
 * The actual instrumentation of the matched methods is performed by static methods within this class,
 * which are annotated by {@link net.bytebuddy.asm.Advice.OnMethodEnter} or {@link net.bytebuddy.asm.Advice.OnMethodExit}.
 * </p>
 * <p>
 * Note: usage of {@link ElasticApmTracer#get()} is discouraged in advices,
 * use the instance provided by {@link ElasticApmInstrumentation#init(ElasticApmTracer)} instead.
 * </p>
 */
public abstract class ElasticApmInstrumentation {

    @Nullable
    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    /**
     * Initializes the advice with the {@link ElasticApmTracer}
     * <p>
     * This enables tests to register a custom instance with a {@link co.elastic.apm.impl.ElasticApmTracerBuilder#configurationRegistry}
     * and {@link co.elastic.apm.impl.ElasticApmTracerBuilder#reporter} which is specific to a particular test or test class.
     * Otherwise, the advice would just have a static reference to {@link ElasticApmTracer#get()},
     * without the possibility to register custom instances.
     * </p>
     *
     * @param tracer the tracer to use for this advice.
     */
    public void init(ElasticApmTracer tracer) {
        ElasticApmInstrumentation.tracer = tracer;
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

}
