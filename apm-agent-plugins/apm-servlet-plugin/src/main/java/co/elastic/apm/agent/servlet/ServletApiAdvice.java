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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.state.GlobalVariables;
import co.elastic.apm.agent.sdk.weakconcurrent.DetachedThreadLocal;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.servlet.helper.ServletTransactionCreationHelper;
import co.elastic.apm.agent.util.TransactionNameUtils;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
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

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_LOW_LEVEL_FRAMEWORK;
import static co.elastic.apm.agent.servlet.ServletTransactionHelper.TRANSACTION_ATTRIBUTE;
import static co.elastic.apm.agent.servlet.ServletTransactionHelper.determineServiceName;

public class ServletApiAdvice {

    private static final String FRAMEWORK_NAME = "Servlet API";
    static final String SPAN_TYPE = "servlet";
    static final String SPAN_SUBTYPE = "request-dispatcher";
    private static final ServletTransactionHelper servletTransactionHelper;
    private static final ServletTransactionCreationHelper servletTransactionCreationHelper;

    static {
        servletTransactionHelper = new ServletTransactionHelper(GlobalTracer.requireTracerImpl());
        servletTransactionCreationHelper = new ServletTransactionCreationHelper(GlobalTracer.requireTracerImpl());
    }

    private static final DetachedThreadLocal<Boolean> excluded = GlobalVariables.get(ServletApiAdvice.class, "excluded", WeakConcurrent.buildThreadLocal());
    private static final DetachedThreadLocal<Object> servletPathTL = GlobalVariables.get(ServletApiAdvice.class, "servletPath", WeakConcurrent.buildThreadLocal());
    private static final DetachedThreadLocal<Object> pathInfoTL = GlobalVariables.get(ServletApiAdvice.class, "pathInfo", WeakConcurrent.buildThreadLocal());

    private static final List<String> requestExceptionAttributes = Arrays.asList(RequestDispatcher.ERROR_EXCEPTION, "exception", "org.springframework.web.servlet.DispatcherServlet.EXCEPTION", "co.elastic.apm.exception");

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterServletService(@Advice.Argument(0) ServletRequest servletRequest) {
        ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            return null;
        }
        AbstractSpan<?> ret = null;
        // re-activate transactions for async requests
        final Transaction transactionAttr = (Transaction) servletRequest.getAttribute(TRANSACTION_ATTRIBUTE);
        if (tracer.currentTransaction() == null && transactionAttr != null) {
            return transactionAttr.activateInScope();
        }

        if (!tracer.isRunning() || !(servletRequest instanceof HttpServletRequest)) {
            return null;
        }

        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        DispatcherType dispatcherType = servletRequest.getDispatcherType();
        CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);

        if (dispatcherType == DispatcherType.REQUEST) {
            if (Boolean.TRUE == excluded.get()) {
                return null;
            }

            ServletContext servletContext = servletRequest.getServletContext();
            if (servletContext != null) {
                ClassLoader servletCL = servletTransactionCreationHelper.getClassloader(servletContext);
                // this makes sure service name discovery also works when attaching at runtime
                determineServiceName(servletContext.getServletContextName(), servletCL, servletContext.getContextPath());
            }

            Transaction transaction = servletTransactionCreationHelper.createAndActivateTransaction(request);

            if (transaction == null) {
                // if the request is excluded, avoid matching all exclude patterns again on each filter invocation
                excluded.set(Boolean.TRUE);
                return null;
            }

            final Request req = transaction.getContext().getRequest();
            if (transaction.isSampled() && coreConfig.isCaptureHeaders()) {
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
            transaction.setFrameworkName(FRAMEWORK_NAME);

            servletTransactionHelper.fillRequestContext(transaction, request.getProtocol(), request.getMethod(), request.isSecure(),
                request.getScheme(), request.getServerName(), request.getServerPort(), request.getRequestURI(), request.getQueryString(),
                request.getRemoteAddr(), request.getHeader("Content-Type"));

            ret = transaction;
        } else if (dispatcherType != DispatcherType.ASYNC &&
            !coreConfig.getDisabledInstrumentations().contains(ServletInstrumentation.SERVLET_API_DISPATCH)) {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent != null) {
                Object servletPath = null;
                Object pathInfo = null;
                RequestDispatcherSpanType spanType = null;
                if (dispatcherType == DispatcherType.FORWARD) {
                    spanType = RequestDispatcherSpanType.FORWARD;
                    servletPath = request.getServletPath();
                    pathInfo = request.getPathInfo();
                } else if (dispatcherType == DispatcherType.INCLUDE) {
                    spanType = RequestDispatcherSpanType.INCLUDE;
                    servletPath = request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
                    pathInfo = request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
                } else if (dispatcherType == DispatcherType.ERROR) {
                    spanType = RequestDispatcherSpanType.ERROR;
                    servletPath = request.getServletPath();
                }

                if (spanType != null && (areNotEqual(servletPathTL.get(), servletPath) || areNotEqual(pathInfoTL.get(), pathInfo))) {
                    ret = parent.createSpan()
                        .appendToName(spanType.getNamePrefix())
                        .withAction(spanType.getAction())
                        .withType(SPAN_TYPE)
                        .withSubtype(SPAN_SUBTYPE);

                    if (servletPath != null) {
                        ret.appendToName(servletPath.toString());
                        servletPathTL.set(servletPath);
                    }
                    if (pathInfo != null) {
                        ret.appendToName(pathInfo.toString());
                        pathInfoTL.set(pathInfo);
                    }
                    ret.activate();
                }
            }
        }
        return ret;
    }

    private static boolean areNotEqual(@Nullable Object first, @Nullable Object second) {
        if (first == null) {
            return second != null;
        } else {
            return !first.equals(second);
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitServletService(@Advice.Argument(0) ServletRequest servletRequest,
                                            @Advice.Argument(1) ServletResponse servletResponse,
                                            @Advice.Enter @Nullable Object transactionOrScopeOrSpan,
                                            @Advice.Thrown @Nullable Throwable t,
                                            @Advice.This Object thiz) {
        ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            return;
        }
        Transaction transaction = null;
        Scope scope = null;
        Span span = null;
        if (transactionOrScopeOrSpan instanceof Transaction) {
            transaction = (Transaction) transactionOrScopeOrSpan;
        } else if (transactionOrScopeOrSpan instanceof Scope) {
            scope = (Scope) transactionOrScopeOrSpan;
        } else if (transactionOrScopeOrSpan instanceof Span) {
            span = (Span) transactionOrScopeOrSpan;
        }

        excluded.remove();
        if (scope != null) {
            scope.close();
        }
        if (thiz instanceof HttpServlet && servletRequest instanceof HttpServletRequest) {
            Transaction currentTransaction = tracer.currentTransaction();
            if (currentTransaction != null) {
                final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
                TransactionNameUtils.setTransactionNameByServletClass(httpServletRequest.getMethod(), thiz.getClass(), currentTransaction.getAndOverrideName(PRIO_LOW_LEVEL_FRAMEWORK));
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
                            if (!attributeName.equals(RequestDispatcher.ERROR_EXCEPTION)) {
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
        if (span != null) {
            servletPathTL.remove();
            pathInfoTL.remove();
            span.captureException(t)
                .withOutcome(t != null ? Outcome.FAILURE : Outcome.SUCCESS)
                .deactivate()
                .end();
        }
    }
}
