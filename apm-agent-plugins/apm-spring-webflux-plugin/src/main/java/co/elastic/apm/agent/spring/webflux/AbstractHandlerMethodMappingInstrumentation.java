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

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ServerWebExchange;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static co.elastic.apm.agent.spring.webflux.WebFluxInstrumentationHelper.*;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class AbstractHandlerMethodMappingInstrumentation extends ElasticApmInstrumentation {
    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(AbstractHandlerMethodMappingInstrumentation.class);

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void afterLookupHandlerMethod(@Advice.Argument(value = 0) ServerWebExchange serverWebExchange,
                                                @Advice.Return(readOnly = false) HandlerMethod handlerMethod) {
        if (tracer == null) {
            logger.trace("afterLookupHandlerMethod tracer == null");
            return;
        }
        ServerHttpRequest request = serverWebExchange.getRequest();
        String name = handlerMethod != null ?
            handlerMethod.getBeanType().getName() + "#" + handlerMethod.getMethod().getName() :
            request.getMethod() + " " + request.getPath().toString();
        Transaction transaction = tracer.startRootTransaction(serverWebExchange.getClass().getClassLoader())
            .withName(name)
            .withType(TRANSACTION_TYPE);
        List<String> values = request.getHeaders().getValuesAsList(CONTENT_LENGTH);
        if (values.size() == 1) {
            transaction.getContext().addCustom(CONTENT_LENGTH, values.get(0));
        }
        transaction.activate();

        serverWebExchange.getAttributes().put(ELASTIC_APM_AGENT_TRANSACTION, transaction);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.web.reactive.result.method.AbstractHandlerMethodMapping");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("lookupHandlerMethod")
            .and(takesArgument(0,
                named("org.springframework.web.server.ServerWebExchange")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("webflux-abstract-handler-method-mapping");
    }
}
