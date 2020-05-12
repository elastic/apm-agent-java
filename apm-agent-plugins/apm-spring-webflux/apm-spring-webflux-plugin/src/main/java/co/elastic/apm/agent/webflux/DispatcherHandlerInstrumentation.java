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
package co.elastic.apm.agent.webflux;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.util.context.Context;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class DispatcherHandlerInstrumentation extends WebFluxInstrumentation {

    public static final String CONTEXT_KEY = "elastic.transaction";

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

        if( tracer == null){
            return;
        }
        transaction = tracer.startRootTransaction(clazz.getClassLoader());
        if (transaction != null) {
            transaction.withName(exchange.getRequest().getPath().value());
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void onExit(@Advice.Local("transaction") @Nullable Transaction transaction,
                               @Advice.Thrown Throwable thrown,
                               @Advice.Return(readOnly = false) Mono<Void> returnValue) {

        if(null == transaction){
            return;
        }

        // in case there is no available mapping, will return a Mono.error() with an attached exception
        // an org.springframework.web.reactive.DispatcherHandler.createNotFoundError
        // -> I'm not sure it's the best way to detect return code, as there might be an error handler

        returnValue = returnValue
            .flatMap(v -> Mono.subscriberContext())
            .map(c -> c.put("", ""))

            .doOnEach(onEach())
            .subscriberContext(addContext(transaction));

    }

    @VisibleForAdvice
    public static Function<Context, Context> addContext(Transaction transaction) {
        return new Function<Context, Context>() {
            @Override
            public Context apply(Context context) {
                return context.put(CONTEXT_KEY, transaction);
            }
        };
    }


    @VisibleForAdvice
    public static Consumer<Signal<Void>> onEach() {
        return new Consumer<Signal<Void>>() {
            @Override
            public void accept(Signal<Void> signal) {
                Transaction transaction = signal.getContext().getOrDefault(CONTEXT_KEY, null);
                if(transaction == null){
                    return;
                }

                // this allows to terminate dispatcher span (if any) on error or success
                switch (signal.getType()) {
                    case ON_COMPLETE:
                    case ON_ERROR:
                        transaction.captureException(signal.getThrowable())
                            .deactivate()
                            .end();
                        break;
                    default:
                        System.out.println("unexpected signal type");
                }
                System.out.println("signal " + signal.getType().name());
            }
        };
    }

//    org.springframework.web.reactive.HandlerAdapter
//    -> interface that is interesting to instrument because it

//    org.springframework.web.reactive.HandlerMapping

}
