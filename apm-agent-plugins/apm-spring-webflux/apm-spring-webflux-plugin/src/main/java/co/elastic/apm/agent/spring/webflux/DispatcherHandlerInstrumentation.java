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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link org.springframework.web.reactive.DispatcherHandler#handle(ServerWebExchange)} that handles functional
 * controller transaction creation and lifecycle through wrapping
 */
public class DispatcherHandlerInstrumentation extends WebFluxInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.web.reactive.DispatcherHandler");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handle")
            .and(takesArgument(0, named("org.springframework.web.server.ServerWebExchange")));
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void onEnter(@Advice.Origin Class<?> clazz,
                                @Advice.Argument(0) ServerWebExchange exchange,
                                @Advice.Local("transaction") Transaction transaction) {

        if (tracer == null) {
            return;
        }
        transaction = tracer.startRootTransaction(clazz.getClassLoader());
        if (transaction != null) {
            transaction.withType("request")
                .activate();

            // TODO : move this to end of exchange lifecycle to prevent reading things to early and mess app encoding
            fillTransactionRequest(transaction, exchange);

            // store transaction in exchange to make it easy to retrieve from other handlers
            exchange.getAttributes().put(TRANSACTION_ATTRIBUTE, transaction);
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void onExit(@Advice.Local("transaction") @Nullable Transaction transaction,
                               @Advice.Thrown @Nullable Throwable thrown,
                               @Advice.Return(readOnly = false) Mono<Void> returnValue) {

        if (null == transaction || thrown != null) {
            return;
        }

        transaction.deactivate();
        // we need to wrap returned mono to terminate transaction
        returnValue = dispatcherWrap(returnValue, transaction);

    }

}
