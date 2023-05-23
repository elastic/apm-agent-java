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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link org.springframework.web.reactive.function.BodyInserters} to discard SSE transactions
 * with functional rouging that should be ignored.
 * <p>
 * The following methods are instrumented:
 * <ul>
 *     <li>{@link org.springframework.web.reactive.function.BodyInserters#fromPublisher(Publisher, Class)}</li>
 *     <li>{@link org.springframework.web.reactive.function.BodyInserters#fromPublisher(Publisher, ParameterizedTypeReference)}</li>
 *     <li>{@link org.springframework.web.reactive.function.BodyInserters#fromProducer(Object, Class)}</li>
 *     <li>{@link org.springframework.web.reactive.function.BodyInserters#fromProducer(Object, ParameterizedTypeReference)}</li>
 * </ul>
 * <p>
 * Ironically, the {@link org.springframework.web.reactive.function.BodyInserters#fromServerSentEvents} does not seem
 * to be called with SSE.
 */

public class BodyInsertersInstrumentation extends WebFluxInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.web.reactive.function.BodyInserters");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("fromPublisher").or(named("fromProducer"))
            .and(takesArgument(1, Class.class).or(takesArgument(1, named("org.springframework.core.ParameterizedTypeReference"))));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.springwebflux.BodyInsertersInstrumentation$OnEnterAdvice";
    }

    public static class OnEnterAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.Argument(1) @Nullable Object arg) {
            if (arg == null) {
                return;
            }

            ResolvableType type = null;
            if (arg instanceof ParameterizedTypeReference) {
                type = ResolvableType.forType(((ParameterizedTypeReference<?>) arg).getType());
            } else if (arg instanceof Class) {
                type = ResolvableType.forClass((Class<?>) arg);
            }

            if (type == null) {
                return;
            }

            if (type.getType().getTypeName().equals(WebfluxHelper.SSE_EVENT_CLASS)) {
                Transaction<?> transaction = GlobalTracer.get().currentTransaction();
                if (transaction != null) {
                    // mark the transaction to be ignored and later discarded
                    transaction.ignoreTransaction();
                }
            }
        }
    }


}
