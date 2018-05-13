/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.spring.webmvc;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Transaction;
import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This {@link HandlerInterceptor} sets the {@link Transaction#name} to the handler responsible for this request.
 * <p>
 * If the handler is a {@link org.springframework.stereotype.Controller}, the {@link Transaction#name} is set to
 * {@code ControllerName#methodName}.
 * If it is a different kind of handler,
 * like a {@link org.springframework.web.servlet.resource.ResourceHttpRequestHandler},
 * the request name is set to the simple class name of the handler.
 * </p>
 * @deprecated the agent automatically instruments Spring
 */
@Deprecated
public class ApmHandlerInterceptor implements HandlerInterceptor {

    private final ElasticApmTracer tracer;

    public ApmHandlerInterceptor() {
        this(ElasticApmTracer.get());
    }

    ApmHandlerInterceptor(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
            SpringTransactionNameInstrumentation.HandlerAdapterAdvice.setName(transaction, className, methodName);
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) {
        // noop
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable  Exception ex) {
        // noop
    }
}
