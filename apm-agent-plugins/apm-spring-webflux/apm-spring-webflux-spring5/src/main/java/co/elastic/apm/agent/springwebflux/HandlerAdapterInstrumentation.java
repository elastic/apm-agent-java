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

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.util.TransactionNameUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments known implementations of {@link org.springframework.web.reactive.HandlerAdapter#handle(ServerWebExchange, Object)}
 * that handle annotation based controllers execution and naming
 * <ul>
 *     <li>{@link org.springframework.web.reactive.function.server.support.HandlerFunctionAdapter}</li>
 *     <li>{@link org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter}</li>
 *     <li>{@link org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter}</li>
 *     <li>{@link org.springframework.web.reactive.result.SimpleHandlerAdapter}</li>
 * </ul>
 */
public class HandlerAdapterInstrumentation extends WebFluxInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.web.reactive.function.server.support.HandlerFunctionAdapter")
            .or(named("org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter"))
            .or(named("org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter"))
            .or(named("org.springframework.web.reactive.result.SimpleHandlerAdapter"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handle")
            .and(takesArgument(0, named("org.springframework.web.server.ServerWebExchange")))
            .and(takesArgument(1, Object.class));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.springwebflux.HandlerAdapterInstrumentation$HandleAdvice";
    }

    public static class HandleAdvice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.Argument(0) ServerWebExchange exchange,
                                     @Advice.Argument(1) Object handler) {

            Object exchangeTransaction = exchange.getAttribute(WebfluxHelper.TRANSACTION_ATTRIBUTE);
            if (exchangeTransaction instanceof Transaction && handler instanceof HandlerMethod) {
                // set name for annotated controllers
                HandlerMethod handlerMethod = (HandlerMethod) handler;

                TransactionNameUtils.setNameFromClassAndMethod(
                    handlerMethod.getBeanType().getSimpleName(),
                    handlerMethod.getMethod().getName(),
                    ((Transaction<?>) exchangeTransaction).getAndOverrideName(AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK, false));

            }

            return exchangeTransaction;
        }

        @Nullable
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static Mono<HandlerResult> onExit(@Advice.Argument(0) ServerWebExchange exchange,
                                                 @Advice.Thrown @Nullable Throwable thrown,
                                                 @Advice.Enter @Nullable Object enterTransaction,
                                                 @Advice.Return @Nullable Mono<HandlerResult> resultMono) {

            if (!(enterTransaction instanceof Transaction) || resultMono == null) {
                return resultMono;
            }

            Transaction<?> transaction = (Transaction<?>) enterTransaction;
            transaction.captureException(thrown);

            if (transaction.isNoop()) {
                // in transaction has been made no-op, we must still deactivate it
                transaction.deactivate();
            }
            // we don't wrap for handler execution, dispatcher will take care of it
            return resultMono;
        }

    }
}
