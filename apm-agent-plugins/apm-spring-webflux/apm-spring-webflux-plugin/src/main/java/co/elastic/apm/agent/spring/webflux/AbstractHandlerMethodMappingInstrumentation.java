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
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.webflux.WebFluxInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ServerWebExchange;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link AbstractHandlerMethodMapping#lookupHandlerMethod} to name transaction from bean on annotated controllers
 */
public class AbstractHandlerMethodMappingInstrumentation extends ElasticApmInstrumentation {

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void afterLookupHandlerMethod(@Advice.Argument(value = 0) ServerWebExchange serverWebExchange,
                                                @Advice.Return @Nullable HandlerMethod handlerMethod) {
        if (tracer == null) {
            return;
        }

        Transaction transaction = serverWebExchange.getAttribute(WebFluxInstrumentation.TRANSACTION_ATTRIBUTE);
        if (transaction == null || handlerMethod == null) {
            return;
        }
        Class<?> beanType = handlerMethod.getBeanType();
        Method method = handlerMethod.getMethod();
        if (beanType == null || method == null) {
            return;
        }

        transaction.withName(String.format("%s#%s", beanType.getName(), handlerMethod.getMethod().getName()));
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
