/*
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
 */
package co.elastic.apm.agent.springwebmvc;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.servlet.Constants;
import co.elastic.apm.agent.servlet.adapter.ServletRequestAdapter;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.util.TransactionNameUtils;
import co.elastic.apm.agent.util.VersionUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.method.HandlerMethod;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK;
import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_LOW_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * This instrumentation sets the transaction name according to the handler responsible for this request.
 * <p>
 * If the handler is a {@link org.springframework.stereotype.Controller}, the transaction name is set to
 * {@code ControllerName#methodName}.
 * If it is a different kind of handler,
 * like a {@link org.springframework.web.servlet.resource.ResourceHttpRequestHandler},
 * the request name is set to the simple class name of the handler.
 * </p>
 * <p>
 * Supports Spring MVC 3.x-6.x through the javax.servlet / jakarta.servlet dependent implementations of this class.
 * </p>
 */
public abstract class AbstractSpringTransactionNameInstrumentation extends TracerAwareInstrumentation {

    private static final String FRAMEWORK_NAME = "Spring Web MVC";

    public abstract Constants.ServletImpl servletImpl();

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
            .and(takesArgument(0, servletImpl().httpRequestClassMatcher()))
            .and(takesArgument(1, servletImpl().httpResponseClassMatcher()))
            .and(takesArgument(2, Object.class));
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // introduced in spring-web 3.1
        return classLoaderCanLoadClass("org.springframework.web.method.HandlerMethod");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("spring-mvc");
    }

    public static class Helper {

        private static final WebConfiguration webConfig = GlobalTracer.get().getConfig(WebConfiguration.class);

        public static <HttpServletRequest> void updateTransactionNameFromHandler(ServletRequestAdapter<HttpServletRequest, ?> adapter, HttpServletRequest request, Object handler) {
            final Transaction<?> transaction = tracer.currentTransaction();
            if (transaction == null) {
                return;
            }
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

            if (!className.isEmpty() && methodName != null) {
                TransactionNameUtils.setNameFromClassAndMethod(
                    className,
                    methodName,
                    transaction.getAndOverrideName(PRIORITY_HIGH_LEVEL_FRAMEWORK)
                );
            } else if (webConfig.isUsePathAsName()) {
                // When method name or class name are not known, we treat the calculated name as a fallback only, thus using lower priority.
                // If delegating to a Servlet, this still allows the better servlet naming default.
                TransactionNameUtils.setNameFromHttpRequestPath(
                    adapter.getMethod(request),
                    adapter.getServletPath(request),
                    adapter.getPathInfo(request),
                    transaction.getAndOverrideName(PRIORITY_LOW_LEVEL_FRAMEWORK + 1),
                    webConfig.getUrlGroups()
                );
            } else if (!className.isEmpty()) {
                // if we are here, then method name is null, thus using lower priority
                TransactionNameUtils.setNameFromClassAndMethod(
                    className,
                    methodName,
                    transaction.getAndOverrideName(PRIORITY_LOW_LEVEL_FRAMEWORK + 1)
                );
            } else {
                // Class name is empty - probably an anonymous handler class
                StringBuilder transactionName = transaction.getAndOverrideName(PRIORITY_LOW_LEVEL_FRAMEWORK + 1);
                if (transactionName != null) {
                    transactionName.append(adapter.getMethod(request)).append(" unknown route");
                }
            }

            transaction.setFrameworkName(FRAMEWORK_NAME);
            transaction.setFrameworkVersion(VersionUtils.getVersion(HandlerMethod.class, "org.springframework", "spring-web"));
        }
    }
}
