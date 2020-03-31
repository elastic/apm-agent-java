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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.RegisterMethodHandle;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.servlet.helper.ServletTransactionCreationHelper;
import co.elastic.apm.agent.util.CallDepth;
import com.blogspot.mydailyjava.weaklockfree.DetachedThreadLocal;

import javax.annotation.Nullable;
import javax.servlet.DispatcherType;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import static co.elastic.apm.agent.bci.ElasticApmInstrumentation.tracer;
import static co.elastic.apm.agent.servlet.ServletTransactionHelper.TRANSACTION_ATTRIBUTE;
import static co.elastic.apm.agent.servlet.ServletTransactionHelper.determineServiceName;

public class ServletApiAdvice {

    private static final ServletTransactionHelper servletTransactionHelper;
    private static final ServletTransactionCreationHelper servletTransactionCreationHelper;

    private static DetachedThreadLocal<Boolean> excluded = new DetachedThreadLocal<Boolean>(DetachedThreadLocal.Cleaner.INLINE) {
        @Override
        protected Boolean initialValue(Thread thread) {
            return Boolean.FALSE;
        }
    };
    private static DetachedThreadLocal<Scope> scopeThreadLocal = new DetachedThreadLocal<Scope>(DetachedThreadLocal.Cleaner.INLINE);

    private static final List<String> requestExceptionAttributes = Arrays.asList("javax.servlet.error.exception", "exception", "org.springframework.web.servlet.DispatcherServlet.EXCEPTION", "co.elastic.apm.exception");

    static {
        servletTransactionHelper = new ServletTransactionHelper(tracer);
        servletTransactionCreationHelper = new ServletTransactionCreationHelper(tracer);
    }

    @Nullable
    @RegisterMethodHandle
    public static Transaction onEnterServletService(ServletRequest servletRequest) {
        int depth = CallDepth.increment(Servlet.class);
        if (tracer == null) {
            return null;
        }
        Transaction transaction = null;
        // re-activate transactions for async requests
        final Transaction transactionAttr = (Transaction) servletRequest.getAttribute(TRANSACTION_ATTRIBUTE);
        if (depth == 0 && tracer.currentTransaction() == null && transactionAttr != null) {
            scopeThreadLocal.set(transactionAttr.activateInScope());
        }
        if (tracer.isRunning() &&
            servletRequest instanceof HttpServletRequest &&
            servletRequest.getDispatcherType() == DispatcherType.REQUEST &&
            !Boolean.TRUE.equals(excluded.get())) {

            ServletContext servletContext = servletRequest.getServletContext();
            if (servletContext != null) {
                // this makes sure service name discovery also works when attaching at runtime
                determineServiceName(servletContext.getServletContextName(), servletContext.getClassLoader(), servletContext.getContextPath());
            }

            final HttpServletRequest request = (HttpServletRequest) servletRequest;
            transaction = servletTransactionCreationHelper.createAndActivateTransaction(request);

            if (transaction == null) {
                // if the request is excluded, avoid matching all exclude patterns again on each filter invocation
                excluded.set(Boolean.TRUE);
                return null;
            }
            final Request req = transaction.getContext().getRequest();
            if (transaction.isSampled() && tracer.getConfig(CoreConfiguration.class).isCaptureHeaders()) {
                if (request.getCookies() != null) {
                    for (Cookie cookie : request.getCookies()) {
                        req.addCookie(cookie.getName(), cookie.getValue());
                    }
                }
                final Enumeration<String> headerNames = request.getHeaderNames();
                if (headerNames != null) {
                    while (headerNames.hasMoreElements()) {
                        final String headerName = headerNames.nextElement();
                        req.addHeader(headerName, request.getHeaders(headerName));
                    }
                }
            }

            servletTransactionHelper.fillRequestContext(transaction, request.getProtocol(), request.getMethod(), request.isSecure(),
                request.getScheme(), request.getServerName(), request.getServerPort(), request.getRequestURI(), request.getQueryString(),
                request.getRemoteAddr(), request.getHeader("Content-Type"));
        }
        return transaction;
    }

    @RegisterMethodHandle
    public static void onExitServletService(ServletRequest servletRequest,
                                            ServletResponse servletResponse,
                                            @Nullable Transaction transaction,
                                            @Nullable Throwable t,
                                            Object thiz) {
        int depth = CallDepth.decrement(Servlet.class);
        if (tracer == null) {
            return;
        }
        excluded.set(Boolean.FALSE);
        Scope scope = scopeThreadLocal.get();
        if (depth == 0 && scope != null) {
            scopeThreadLocal.clear();
            scope.close();
        }
        if (thiz instanceof HttpServlet && servletRequest instanceof HttpServletRequest) {
            Transaction currentTransaction = tracer.currentTransaction();
            if (currentTransaction != null) {
                final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
                ServletTransactionHelper.setTransactionNameByServletClass(httpServletRequest.getMethod(), thiz.getClass(), currentTransaction);
                final Principal userPrincipal = httpServletRequest.getUserPrincipal();
                ServletTransactionHelper.setUsernameIfUnset(userPrincipal != null ? userPrincipal.getName() : null, currentTransaction.getContext());
            }
        }
        if (transaction != null &&
            servletRequest instanceof HttpServletRequest &&
            servletResponse instanceof HttpServletResponse) {

            final HttpServletRequest request = (HttpServletRequest) servletRequest;
            if (request.getAttribute(ServletTransactionHelper.ASYNC_ATTRIBUTE) != null) {
                // HttpServletRequest.startAsync was invoked on this request.
                // The transaction should be handled from now on by the other thread committing the response
                transaction.deactivate();
            } else {
                // this is not an async request, so we can end the transaction immediately
                final HttpServletResponse response = (HttpServletResponse) servletResponse;
                if (transaction.isSampled() && tracer.getConfig(CoreConfiguration.class).isCaptureHeaders()) {
                    final Response resp = transaction.getContext().getResponse();
                    for (String headerName : response.getHeaderNames()) {
                        resp.addHeader(headerName, response.getHeaders(headerName));
                    }
                }
                // request.getParameterMap() may allocate a new map, depending on the servlet container implementation
                // so only call this method if necessary
                final String contentTypeHeader = request.getHeader("Content-Type");
                final Map<String, String[]> parameterMap;
                if (transaction.isSampled() && servletTransactionHelper.captureParameters(request.getMethod(), contentTypeHeader)) {
                    parameterMap = request.getParameterMap();
                } else {
                    parameterMap = null;
                }

                Throwable t2 = null;
                boolean overrideStatusCodeOnThrowable = true;
                if (t == null) {
                    final int size = requestExceptionAttributes.size();
                    for (int i = 0; i < size; i++) {
                        String attributeName = requestExceptionAttributes.get(i);
                        Object throwable = request.getAttribute(attributeName);
                        if (throwable instanceof Throwable) {
                            t2 = (Throwable) throwable;
                            if (!attributeName.equals("javax.servlet.error.exception")) {
                                overrideStatusCodeOnThrowable = false;
                            }
                            break;
                        }
                    }
                }

                servletTransactionHelper.onAfter(transaction, t == null ? t2 : t, response.isCommitted(), response.getStatus(),
                    overrideStatusCodeOnThrowable, request.getMethod(), parameterMap, request.getServletPath(),
                    request.getPathInfo(), contentTypeHeader, true
                );
            }
        }
    }
}
