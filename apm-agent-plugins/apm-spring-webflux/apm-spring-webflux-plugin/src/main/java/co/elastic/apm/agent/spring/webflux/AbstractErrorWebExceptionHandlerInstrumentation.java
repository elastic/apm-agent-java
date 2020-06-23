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
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.spring.webflux.WebFluxInstrumentationHelper.ELASTIC_APM_AGENT_TRANSACTION;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class AbstractErrorWebExceptionHandlerInstrumentation extends ElasticApmInstrumentation {
    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(AbstractErrorWebExceptionHandlerInstrumentation.class);

    @SuppressWarnings("unused")
    @Advice.OnMethodEnter
    public static void beforeHandleMethod(@Advice.Argument(value = 0) ServerWebExchange serverWebExchange,
                                          @Advice.Argument(value = 1) Throwable throwable) {
        if (tracer == null) {
            logger.trace("beforeHandleMethod tracer == null");
            return;
        }
        Transaction transaction = (Transaction) serverWebExchange.getAttributes().remove(ELASTIC_APM_AGENT_TRANSACTION);
        if (transaction != null) {
            if (throwable instanceof ResponseStatusException) {
                ResponseStatusException e = (ResponseStatusException) throwable;
                transaction.withResult(ResultUtil.getResultByHttpStatus(e.getStatus().value()));
            }
            transaction
                .captureException(throwable)
                .deactivate()
                .end();
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handle")
            .and(takesArgument(0,
                named("org.springframework.web.server.ServerWebExchange")))
            .and(takesArgument(1,
                named("java.lang.Throwable")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("webflux-error-web-exception-handler");
    }
}
