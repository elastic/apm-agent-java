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
package co.elastic.apm.agent.spring.webflux;

import co.elastic.apm.agent.impl.transaction.Transaction;
import com.sun.nio.sctp.HandlerResult;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link org.springframework.web.reactive.HandlerAdapter#handle(ServerWebExchange, Object)} that handles
 * annotation based controllers execution & naming
 */
public class HandlerAdapterInstrumentation extends WebFluxInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("org.springframework.web.reactive")
            .and(hasSuperType(named("org.springframework.web.reactive.HandlerAdapter")))
            .and(not(isInterface()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handle")
            .and(takesArgument(0, named("org.springframework.web.server.ServerWebExchange")))
            .and(takesArgument(1, Object.class));
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void onEnter(@Advice.Argument(0) ServerWebExchange exchange,
                                @Advice.Argument(1) Object handler,
                                @Advice.Local("transaction") Transaction transaction) {

        Object attribute = exchange.getAttribute(TRANSACTION_ATTRIBUTE);
        if (attribute instanceof Transaction) {
            transaction = (Transaction) attribute;
            transaction.activate();

            if (handler instanceof HandlerMethod) {
                // set name for annotated controllers
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                transaction.withName(String.format("%s#%s", handlerMethod.getBeanType().getSimpleName(), handlerMethod.getMethod().getName()));
            }
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void onExit(@Advice.Thrown Throwable thrown,
                               @Advice.Local("transaction") @Nullable  Transaction transaction,
                               @Advice.Return(readOnly = false) @Nullable Mono<HandlerResult> resultMono) {

        if (transaction != null) {
            transaction.captureException(thrown)
                .deactivate();

            if (resultMono != null) {
                // might happen when an error is triggered server-side
                resultMono = handlerWrap(resultMono, transaction);
            }

        }
    }
}
