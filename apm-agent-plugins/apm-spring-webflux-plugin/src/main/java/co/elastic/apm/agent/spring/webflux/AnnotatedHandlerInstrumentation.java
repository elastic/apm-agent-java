/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;


/**
 * This class instruments all methods annotated with @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping in
 * a class which is annotated with @Controller or @RestController annotation. Methods without ServerWebExchange or ServerHttpRequest
 * argument types won't be instrumented.
 */
public class AnnotatedHandlerInstrumentation extends ElasticApmInstrumentation {

    /**
     * Starts a transaction only if given controller method
     * has an argument of org.springframework.web.server.ServerWebExchange or
     * org.springframework.http.server.reactive.ServerHttpRequest type.
     *
     * @param args array of arguments passed to this controller method
     * @param transaction actual Transaction or null
     * @param serverWebExchange passed ServerWebExchange or null
     * @param serverHttpRequest passed ServerHttpRequest or null
     */
    @SuppressWarnings("unused")
    @VisibleForAdvice
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onBeforeExecute(@Advice.AllArguments final Object[] args,
                                       @Advice.Local("transaction") @Nullable Transaction transaction,
                                       @Advice.Local("serverWebExchange") @Nullable ServerWebExchange serverWebExchange,
                                       @Advice.Local("serverHttpRequest") @Nullable ServerHttpRequest serverHttpRequest) {
        if (tracer == null) {
            return;
        }
        if (tracer.getActive() != null) {
            return;
        }

        if (transaction != null) {
            return;
        }

        if (serverHttpRequest == null) {
            serverHttpRequest = WebFluxInstrumentationHelper.findServerHttpRequest(args);
        }
        if (serverHttpRequest != null) {
            transaction = WebFluxInstrumentationHelper.createAndActivateTransaction(tracer, serverHttpRequest);
            return;
        }

        if (serverWebExchange == null) {
            serverWebExchange = WebFluxInstrumentationHelper.findServerWebExchange(args);
        }
        if (serverWebExchange != null) {
            transaction = WebFluxInstrumentationHelper.createAndActivateTransaction(tracer, serverWebExchange);
        }
    }

    @SuppressWarnings("unused")
    @VisibleForAdvice
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onAfterExecute(@Advice.AllArguments final Object[] args,
                                      @Advice.Local("transaction") @Nullable Transaction transaction,
                                      @Advice.Local("serverWebExchange") @Nullable ServerWebExchange serverWebExchange,
                                      @Advice.Local("serverHttpRequest") @Nullable ServerHttpRequest serverHttpRequest,
                                      @Advice.Thrown Throwable t) {

        if (transaction != null) {
            transaction.captureException(t)
                .deactivate()
                .end();
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return isAnnotatedWith(named("org.springframework.web.bind.annotation.Controller"))
            .or(isAnnotatedWith(named("org.springframework.web.bind.annotation.RestController")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isAnnotatedWith(named("org.springframework.web.bind.annotation.GetMapping"))
            .or(isAnnotatedWith(named("org.springframework.web.bind.annotation.PostMapping")))
            .or(isAnnotatedWith(named("org.springframework.web.bind.annotation.DeleteMapping")))
            .or(isAnnotatedWith(named("org.springframework.web.bind.annotation.PatchMapping")))
            .or(isAnnotatedWith(named("org.springframework.web.bind.annotation.PutMapping")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("webflux-annotated-handler");
    }
}
