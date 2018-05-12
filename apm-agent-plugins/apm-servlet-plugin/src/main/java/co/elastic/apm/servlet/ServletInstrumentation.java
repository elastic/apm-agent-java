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
package co.elastic.apm.servlet;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.context.Response;
import co.elastic.apm.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Enumeration;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments servlets to create transactions.
 * <p>
 * If the transaction has already been recorded with the help of {@link FilterChainInstrumentation},
 * it does not record the transaction again.
 * But if there is no filter registered for a servlet,
 * this makes sure to record a transaction in that case.
 * </p>
 */
public class ServletInstrumentation extends ElasticApmInstrumentation {

    @Override
    public void init(ElasticApmTracer tracer) {
        ServletAdvice.init(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            // the hasSuperType matcher is quite costly,
            // as the inheritance hierarchy of each class would have to be examined
            // this pre-selects candidates and hopefully does not cause lots of false negatives
            .and(nameContains("Servlet"))
            .and(hasSuperType(named("javax.servlet.http.HttpServlet")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("service")
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")));
    }

    @Override
    public Class<?> getAdviceClass() {
        return ServletAdvice.class;
    }

    /**
     * Only the methods annotated with {@link Advice.OnMethodEnter} and {@link Advice.OnMethodExit} may contain references to
     * {@code javax.servlet}, as these are inlined into the matching methods.
     * The agent itself does not have access to the Servlet API classes, as they are loaded by a child class loader.
     * See https://github.com/raphw/byte-buddy/issues/465 for more information.
     */
    public static class ServletAdvice {

        @Nullable
        @VisibleForAdvice
        public static ServletTransactionHelper servletTransactionHelper;


        static void init(ElasticApmTracer tracer) {
            servletTransactionHelper = new ServletTransactionHelper(tracer);
        }

        @Nullable
        @Advice.OnMethodEnter
        public static Transaction onEnterServletService(@Advice.Argument(0) HttpServletRequest request) {
            if (servletTransactionHelper != null) {
                return servletTransactionHelper.onBefore(request.getServletPath(), request.getPathInfo(),
                    request.getRequestURI(), request.getHeader("User-Agent"));
            }
            return null;
        }

        @Advice.OnMethodExit(onThrowable = Exception.class)
        public static void onExitServletService(@Advice.Argument(0) HttpServletRequest request,
                                                @Advice.Argument(1) HttpServletResponse response,
                                                @Advice.Enter @Nullable Transaction transaction,
                                                @Advice.Thrown @Nullable Exception exception) {
            if (servletTransactionHelper != null && transaction != null) {
                final Request req = transaction.getContext().getRequest();
                if (request.getCookies() != null) {
                    for (Cookie cookie : request.getCookies()) {
                        req.addCookie(cookie.getName(), cookie.getValue());
                    }
                }
                final Enumeration headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    final String headerName = (String) headerNames.nextElement();
                    req.addHeader(headerName, request.getHeader(headerName));
                }
                final Response resp = transaction.getContext().getResponse();
                for (String headerName : response.getHeaderNames()) {
                    resp.addHeader(headerName, response.getHeader(headerName));
                }
                final Principal userPrincipal = request.getUserPrincipal();
                servletTransactionHelper.onAfter(transaction, exception, userPrincipal != null ? userPrincipal.getName() : null,
                    request.getProtocol(), request.getMethod(), request.isSecure(), request.getScheme(), request.getServerName(),
                    request.getServerPort(), request.getRequestURI(), request.getQueryString(), request.getParameterMap(),
                    request.getRemoteAddr(), request.getRequestURL(), response.isCommitted(), response.getStatus());
            }
        }
    }


}
