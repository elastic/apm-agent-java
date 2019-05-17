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
package co.elastic.apm.agent.spring.webmvc;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.method.HandlerMethod;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * This instrumentation sets the {@link Transaction#name} according to the handler responsible for this request.
 * <p>
 * If the handler is a {@link org.springframework.stereotype.Controller}, the {@link Transaction#name} is set to
 * {@code ControllerName#methodName}.
 * If it is a different kind of handler,
 * like a {@link org.springframework.web.servlet.resource.ResourceHttpRequestHandler},
 * the request name is set to the simple class name of the handler.
 * </p>
 * <p>
 * Supports Spring MVC 3.x-5.x
 * </p>
 */
public class SpringTransactionNameInstrumentation extends ElasticApmInstrumentation {

    /**
     * Instrumenting well defined interfaces like {@link org.springframework.web.servlet.HandlerAdapter}
     * is preferred over instrumenting private methods like
     * {@link org.springframework.web.servlet.DispatcherServlet#getHandler(javax.servlet.http.HttpServletRequest)},
     * as interfaces should be more stable.
     */
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("org.springframework.web.servlet")
            .and(hasSuperType(named("org.springframework.web.servlet.HandlerAdapter")))
            .and(not(isInterface()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handle")
            .and(returns(named("org.springframework.web.servlet.ModelAndView")))
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
            .and(takesArgument(2, Object.class));
    }

    @Override
    public Class<?> getAdviceClass() {
        return HandlerAdapterAdvice.class;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("spring-mvc");
    }

    @VisibleForAdvice
    public static class HandlerAdapterAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        static void setTransactionName(@Advice.Argument(2) Object handler) {
            if (tracer != null) {
                final Transaction transaction = tracer.currentTransaction();
                if (transaction != null) {
                    final String className;
                    final String methodName;
                    if (handler instanceof HandlerMethod) {
                        HandlerMethod handlerMethod = ((HandlerMethod) handler);
                        className = handlerMethod.getBeanType().getSimpleName();
                        methodName = handlerMethod.getMethod().getName();
                    } else {
                        className = handler.getClass().getSimpleName();
                        methodName = null;
                    }
                    setName(transaction, className, methodName);
                }
            }
        }

        @VisibleForAdvice
        public static void setName(Transaction transaction, String className, @Nullable String methodName) {
            final StringBuilder name = transaction.getName();
            name.setLength(0);
            name.append(className);
            if (methodName != null) {
                name.append('#').append(methodName);
            }
        }
    }
}
