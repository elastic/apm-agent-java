/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.context.Response;
import co.elastic.apm.impl.transaction.TraceContext;
import co.elastic.apm.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Enumeration;

/**
 * Only the methods annotated with {@link Advice.OnMethodEnter} and {@link Advice.OnMethodExit} may contain references to
 * {@code javax.servlet}, as these are inlined into the matching methods.
 * The agent itself does not have access to the Servlet API classes, as they are loaded by a child class loader.
 * See https://github.com/raphw/byte-buddy/issues/465 for more information.
 */
public class ServletApiAdvice {

    @Nullable
    @VisibleForAdvice
    public static ServletTransactionHelper servletTransactionHelper;

    static void init(ElasticApmTracer tracer) {
        servletTransactionHelper = new ServletTransactionHelper(tracer);
    }

    @Nullable
    @Advice.OnMethodEnter
    public static Transaction onEnterServletService(@Advice.Argument(0) ServletRequest servletRequest) {
        if (servletTransactionHelper != null &&
            servletRequest instanceof HttpServletRequest &&
            !Boolean.TRUE.equals(servletRequest.getAttribute(FilterChainInstrumentation.EXCLUDE_REQUEST))) {

            final HttpServletRequest request = (HttpServletRequest) servletRequest;
            final Transaction transaction = servletTransactionHelper.onBefore(
                request.getServletPath(), request.getPathInfo(),
                request.getRequestURI(), request.getHeader("User-Agent"),
                request.getHeader(TraceContext.TRACE_PARENT_HEADER));
            if (transaction == null) {
                // if the request is excluded, avoid matching all exclude patterns again on each filter invocation
                request.setAttribute(FilterChainInstrumentation.EXCLUDE_REQUEST, Boolean.TRUE);
                return null;
            }
            final Request req = transaction.getContext().getRequest();
            if (transaction.isSampled() && request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    req.addCookie(cookie.getName(), cookie.getValue());
                }
            }
            final Enumeration headerNames = request.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    final String headerName = (String) headerNames.nextElement();
                    req.addHeader(headerName, request.getHeader(headerName));
                }
            }

            final Principal userPrincipal = request.getUserPrincipal();
            servletTransactionHelper.fillRequestContext(transaction, userPrincipal != null ? userPrincipal.getName() : null,
                request.getProtocol(), request.getMethod(), request.isSecure(), request.getScheme(), request.getServerName(),
                request.getServerPort(), request.getRequestURI(), request.getQueryString(), request.getRemoteAddr(), request.getRequestURL());
            return transaction;
        }
        return null;
    }

    @Advice.OnMethodExit(onThrowable = Exception.class)
    public static void onExitServletService(@Advice.Argument(0) ServletRequest servletRequest,
                                            @Advice.Argument(1) ServletResponse servletResponse,
                                            @Advice.Enter @Nullable Transaction transaction,
                                            @Advice.Thrown @Nullable Exception exception) {
        if (servletTransactionHelper != null &&
            transaction != null &&
            servletRequest instanceof HttpServletRequest &&
            servletResponse instanceof HttpServletResponse) {

            final HttpServletResponse response = (HttpServletResponse) servletResponse;
            final HttpServletRequest request = (HttpServletRequest) servletRequest;
            final Response resp = transaction.getContext().getResponse();
            for (String headerName : response.getHeaderNames()) {
                resp.addHeader(headerName, response.getHeader(headerName));
            }

            servletTransactionHelper.onAfter(transaction, exception, response.isCommitted(), response.getStatus(), request.getMethod(),
                request.getParameterMap());
        }
    }
}
