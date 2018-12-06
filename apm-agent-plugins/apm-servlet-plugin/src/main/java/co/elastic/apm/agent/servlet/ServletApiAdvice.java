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
import co.elastic.apm.impl.Scope;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.context.Response;
import co.elastic.apm.impl.transaction.TraceContext;
import co.elastic.apm.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import javax.servlet.DispatcherType;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
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

    @VisibleForAdvice
    public static final String TRANSACTION_ATTRIBUTE = ServletApiAdvice.class.getName() + ".transaction";
    @Nullable
    @VisibleForAdvice
    public static ServletTransactionHelper servletTransactionHelper;
    @Nullable
    @VisibleForAdvice
    public static ElasticApmTracer tracer;
    @VisibleForAdvice
    public static ThreadLocal<Boolean> excluded = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    static void init(ElasticApmTracer tracer) {
        ServletApiAdvice.tracer = tracer;
        servletTransactionHelper = new ServletTransactionHelper(tracer);
    }

    @Nullable
    @Advice.OnMethodEnter
    public static void onEnterServletService(@Advice.Argument(0) ServletRequest servletRequest,
                                             @Advice.Local("transaction") Transaction transaction,
                                             @Advice.Local("scope") Scope scope) {
        if (tracer == null) {
            return;
        }
        // re-activate transactions for async requests
        final Transaction transactionAttr = (Transaction) servletRequest.getAttribute(TRANSACTION_ATTRIBUTE);
        if (tracer.currentTransaction() == null && transactionAttr != null) {
            scope = transactionAttr.activateInScope();
        }
        if (servletTransactionHelper != null &&
            servletRequest instanceof HttpServletRequest &&
            servletRequest.getDispatcherType() == DispatcherType.REQUEST &&
            !Boolean.TRUE.equals(excluded.get())) {

            final HttpServletRequest request = (HttpServletRequest) servletRequest;
            transaction = servletTransactionHelper.onBefore(
                request.getServletPath(), request.getPathInfo(),
                request.getRequestURI(), request.getHeader("User-Agent"),
                request.getHeader(TraceContext.TRACE_PARENT_HEADER));
            if (transaction == null) {
                // if the request is excluded, avoid matching all exclude patterns again on each filter invocation
                excluded.set(Boolean.TRUE);
                return;
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

            servletTransactionHelper.fillRequestContext(transaction, request.getProtocol(), request.getMethod(), request.isSecure(),
                request.getScheme(), request.getServerName(), request.getServerPort(), request.getRequestURI(), request.getQueryString(),
                request.getRemoteAddr());
        }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExitServletService(@Advice.Argument(0) ServletRequest servletRequest,
                                            @Advice.Argument(1) ServletResponse servletResponse,
                                            @Advice.Local("transaction") @Nullable Transaction transaction,
                                            @Advice.Local("scope") @Nullable Scope scope,
                                            @Advice.Thrown @Nullable Throwable t,
                                            @Advice.This Object thiz) {
        if (tracer == null) {
            return;
        }
        excluded.set(Boolean.FALSE);
        if (scope != null) {
            scope.close();
        }
        if (thiz instanceof HttpServlet && servletRequest instanceof HttpServletRequest) {
            Transaction currentTransaction = tracer.currentTransaction();
            if (currentTransaction != null) {
                final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
                ServletTransactionHelper.setTransactionNameByServletClass(httpServletRequest.getMethod(), thiz.getClass(), currentTransaction.getName());
                final Principal userPrincipal = httpServletRequest.getUserPrincipal();
                ServletTransactionHelper.setUsernameIfUnset(userPrincipal != null ? userPrincipal.getName() : null, currentTransaction.getContext());
            }
        }
        if (servletTransactionHelper != null &&
            transaction != null &&
            servletRequest instanceof HttpServletRequest &&
            servletResponse instanceof HttpServletResponse) {

            final HttpServletRequest request = (HttpServletRequest) servletRequest;
            if (request.isAsyncStarted()) {
                // the response is not ready yet; the request is handled asynchronously
                transaction.deactivate();
            } else {
                // this is not an async request, so we can end the transaction immediately
                final Response resp = transaction.getContext().getResponse();
                final HttpServletResponse response = (HttpServletResponse) servletResponse;
                for (String headerName : response.getHeaderNames()) {
                    resp.addHeader(headerName, response.getHeader(headerName));
                }
                request.removeAttribute(TRANSACTION_ATTRIBUTE);
                servletTransactionHelper.onAfter(transaction, t, response.isCommitted(), response.getStatus(), request.getMethod(),
                    request.getParameterMap(), request.getServletPath(), request.getPathInfo());
            }
        }
    }
}
