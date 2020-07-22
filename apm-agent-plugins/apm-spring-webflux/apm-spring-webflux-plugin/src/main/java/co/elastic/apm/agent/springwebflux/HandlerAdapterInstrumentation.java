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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import com.sun.nio.sctp.HandlerResult;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments known implementations of {@link org.springframework.web.reactive.HandlerAdapter#handle(ServerWebExchange, Object)}
 * that handle annotation based controllers execution & naming
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

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(@Advice.Argument(0) ServerWebExchange exchange,
                                 @Advice.Argument(1) Object handler) {

        Object attribute = exchange.getAttribute(TRANSACTION_ATTRIBUTE);
        if (attribute instanceof Transaction) {
            Transaction transaction = (Transaction) attribute;
            transaction.activate();

            if (handler instanceof HandlerMethod) {
                // store name for annotated controllers
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                exchange.getAttributes().put(ANNOTATED_BEAN_NAME_ATTRIBUTE, handlerMethod.getBeanType().getSimpleName());
                exchange.getAttributes().put(ANNOTATED_METHOD_NAME_ATTRIBUTE, handlerMethod.getMethod().getName());
            }
        }

        return attribute;
    }

    @Nullable
    @AssignTo.Return
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static Mono<HandlerResult> onExit(@Advice.Argument(0) ServerWebExchange exchange,
                                             @Advice.Thrown Throwable thrown,
                                             @Advice.Enter @Nullable Object enterTransaction,
                                             @Advice.Return @Nullable Mono<HandlerResult> resultMono) {

        if (enterTransaction instanceof Transaction) {
            Transaction transaction = (Transaction) enterTransaction;
            transaction.captureException(thrown)
                .deactivate();

            if (resultMono != null) {
                // might happen when an error is triggered server-side
                resultMono = handlerWrap(resultMono, transaction, exchange, "handler-adapter");
            }

        }

        return resultMono;
    }
}
