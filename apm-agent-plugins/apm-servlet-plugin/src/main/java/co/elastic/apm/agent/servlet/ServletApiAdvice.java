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
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.sdk.state.GlobalVariables;
import co.elastic.apm.agent.sdk.weakconcurrent.DetachedThreadLocal;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.servlet.adapter.ServletApiAdapter;
import co.elastic.apm.agent.tracer.metadata.Request;
import co.elastic.apm.agent.tracer.metadata.Response;
import co.elastic.apm.agent.util.TransactionNameUtils;
import co.elastic.apm.agent.tracer.Scope;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_LOW_LEVEL_FRAMEWORK;
import static co.elastic.apm.agent.servlet.ServletTransactionHelper.TRANSACTION_ATTRIBUTE;

public abstract class ServletApiAdvice {

    private static final String FRAMEWORK_NAME = "Servlet API";
    static final String SPAN_TYPE = "servlet";
    static final String SPAN_SUBTYPE = "request-dispatcher";
    private static final ServletTransactionHelper servletTransactionHelper = new ServletTransactionHelper(GlobalTracer.get());

    private static final DetachedThreadLocal<Boolean> excluded = GlobalVariables.get(ServletApiAdvice.class, "excluded", WeakConcurrent.<Boolean>buildThreadLocal());
    private static final DetachedThreadLocal<Object> servletPathTL = GlobalVariables.get(ServletApiAdvice.class, "servletPath", WeakConcurrent.buildThreadLocal());
    private static final DetachedThreadLocal<Object> pathInfoTL = GlobalVariables.get(ServletApiAdvice.class, "pathInfo", WeakConcurrent.buildThreadLocal());

    private static final List<String> requestExceptionAttributes = Arrays.asList("javax.servlet.error.exception", "jakarta.servlet.error.exception", "exception", "org.springframework.web.servlet.DispatcherServlet.EXCEPTION", "co.elastic.apm.exception");

    @Nullable
    public static <HttpServletRequest, HttpServletResponse, ServletContext, ServletContextEvent, FilterConfig, ServletConfig> Object onServletEnter(
        ServletApiAdapter<HttpServletRequest, HttpServletResponse, ServletContext, ServletContextEvent, FilterConfig, ServletConfig> adapter,
        Object servletRequest) {

        Tracer tracer = GlobalTracer.get().require(Tracer.class);
        if (tracer == null) {
            return null;
        }

        final HttpServletRequest httpServletRequest = adapter.asHttpServletRequest(servletRequest);
        if (httpServletRequest == null) {
            return null;
        }
        AbstractSpan<?> ret = null;
        // re-activate transactions for async requests
        final Transaction<?> transactionAttr = (Transaction<?>) adapter.getAttribute(httpServletRequest, TRANSACTION_ATTRIBUTE);
        if (tracer.currentTransaction() == null && transactionAttr != null) {
            return transactionAttr.activateInScope();
        }

        if (!tracer.isRunning()) {
            return null;
        }

        CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);

        if (adapter.isRequestDispatcherType(httpServletRequest)) {
            if (Boolean.TRUE == excluded.get()) {
                return null;
            }

            ServletServiceNameHelper.determineServiceName(adapter, adapter.getServletContext(httpServletRequest), tracer);

            Transaction<?> transaction = servletTransactionHelper.createAndActivateTransaction(adapter, adapter, httpServletRequest);

            if (transaction == null) {
                // if the httpServletRequest is excluded, avoid matching all exclude patterns again on each filter invocation
                excluded.set(Boolean.TRUE);
                return null;
            }

            final Request req = transaction.getContext().getRequest();
            if (transaction.isSampled() && coreConfig.isCaptureHeaders()) {
                adapter.handleCookies(req, httpServletRequest);

                final Enumeration<String> headerNames = adapter.getRequestHeaderNames(httpServletRequest);
                if (headerNames != null) {
                    while (headerNames.hasMoreElements()) {
                        final String headerName = headerNames.nextElement();
                        req.addHeader(headerName, adapter.getRequestHeaders(httpServletRequest, headerName));
                    }
                }
            }
            transaction.setFrameworkName(FRAMEWORK_NAME);

            servletTransactionHelper.fillRequestContext(transaction, adapter.getProtocol(httpServletRequest), adapter.getMethod(httpServletRequest), adapter.isSecure(httpServletRequest),
                adapter.getScheme(httpServletRequest), adapter.getServerName(httpServletRequest), adapter.getServerPort(httpServletRequest), adapter.getRequestURI(httpServletRequest), adapter.getQueryString(httpServletRequest),
                adapter.getRemoteAddr(httpServletRequest), adapter.getHeader(httpServletRequest, "Content-Type"));

            ret = transaction;
        } else if (!adapter.isAsyncDispatcherType(httpServletRequest) && coreConfig.isInstrumentationEnabled(Constants.SERVLET_API_DISPATCH)) {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent != null) {
                Object servletPath = null;
                Object pathInfo = null;
                RequestDispatcherSpanType spanType = null;
                if (adapter.isForwardDispatcherType(httpServletRequest)) {
                    spanType = RequestDispatcherSpanType.FORWARD;
                    servletPath = adapter.getServletPath(httpServletRequest);
                    pathInfo = adapter.getPathInfo(httpServletRequest);
                } else if (adapter.isIncludeDispatcherType(httpServletRequest)) {
                    spanType = RequestDispatcherSpanType.INCLUDE;
                    servletPath = adapter.getIncludeServletPathAttribute(httpServletRequest);
                    pathInfo = adapter.getIncludePathInfoAttribute(httpServletRequest);
                } else if (adapter.isErrorDispatcherType(httpServletRequest)) {
                    spanType = RequestDispatcherSpanType.ERROR;
                    servletPath = adapter.getServletPath(httpServletRequest);
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

    public static <HttpServletRequest, HttpServletResponse, ServletContext, ServletContextEvent, FilterConfig, ServletConfig> void onExitServlet(
        ServletApiAdapter<HttpServletRequest, HttpServletResponse, ServletContext, ServletContextEvent, FilterConfig, ServletConfig> adapter,
        Object servletRequest,
        Object servletResponse,
        @Nullable Object transactionOrScopeOrSpan,
        @Nullable Throwable t,
        Object thiz) {

        Tracer tracer = GlobalTracer.get();

        Transaction<?> transaction = null;
        Scope scope = null;
        Span<?> span = null;
        if (transactionOrScopeOrSpan instanceof Transaction<?>) {
            transaction = (Transaction<?>) transactionOrScopeOrSpan;
        } else if (transactionOrScopeOrSpan instanceof Scope) {
            scope = (Scope) transactionOrScopeOrSpan;
        } else if (transactionOrScopeOrSpan instanceof Span<?>) {
            span = (Span<?>) transactionOrScopeOrSpan;
        }

        excluded.remove();
        if (scope != null) {
            scope.close();
        }
        HttpServletRequest httpServletRequest = adapter.asHttpServletRequest(servletRequest);
        HttpServletResponse httpServletResponse = adapter.asHttpServletResponse(servletResponse);
        if (adapter.isInstanceOfHttpServlet(thiz) && httpServletRequest != null) {
            Transaction<?> currentTransaction = tracer.currentTransaction();
            if (currentTransaction != null) {
                TransactionNameUtils.setTransactionNameByServletClass(adapter.getMethod(httpServletRequest), thiz.getClass(), currentTransaction.getAndOverrideName(PRIORITY_LOW_LEVEL_FRAMEWORK));

                String userName = ServletTransactionHelper.getUserFromPrincipal(adapter.getUserPrincipal(httpServletRequest));
                if (userName != null) {
                    ServletTransactionHelper.setUsernameIfUnset(userName, currentTransaction.getContext());
                }
            }
        }
        if (transaction != null &&
            httpServletRequest != null &&
            httpServletResponse != null) {

            if (adapter.getHttpAttribute(httpServletRequest, ServletTransactionHelper.ASYNC_ATTRIBUTE) != null) {
                // HttpServletRequest.startAsync was invoked on this httpServletRequest.
                // The transaction should be handled from now on by the other thread committing the response
                transaction.deactivate();
            } else {
                // this is not an async httpServletRequest, so we can end the transaction immediately
                if (transaction.isSampled() && tracer.getConfig(CoreConfiguration.class).isCaptureHeaders()) {
                    final Response resp = transaction.getContext().getResponse();
                    for (String headerName : adapter.getHeaderNames(httpServletResponse)) {
                        resp.addHeader(headerName, adapter.getHeaders(httpServletResponse, headerName));
                    }
                }
                // httpServletRequest.getParameterMap() may allocate a new map, depending on the servlet container implementation
                // so only call this method if necessary
                final String contentTypeHeader = adapter.getHeader(httpServletRequest, "Content-Type");
                final Map<String, String[]> parameterMap;
                if (transaction.isSampled() && servletTransactionHelper.captureParameters(adapter.getMethod(httpServletRequest), contentTypeHeader)) {
                    parameterMap = adapter.getParameterMap(httpServletRequest);
                } else {
                    parameterMap = null;
                }

                Throwable t2 = null;
                boolean overrideStatusCodeOnThrowable = true;
                if (t == null) {
                    final int size = requestExceptionAttributes.size();
                    for (int i = 0; i < size; i++) {
                        String attributeName = requestExceptionAttributes.get(i);
                        Object throwable = adapter.getHttpAttribute(httpServletRequest, attributeName);
                        if (throwable instanceof Throwable) {
                            t2 = (Throwable) throwable;
                            if (!attributeName.equals("javax.servlet.error.exception") && !attributeName.equals("jakarta.servlet.error.exception")) {
                                overrideStatusCodeOnThrowable = false;
                            }
                            break;
                        }
                    }
                }

                ServletContext servletContext = adapter.getServletContext(httpServletRequest);
                String servletPath = adapter.getServletPath(httpServletRequest);
                String pathInfo = adapter.getPathInfo(httpServletRequest);
                if ((servletPath == null || servletPath.isEmpty()) && servletContext != null) {
                    String contextPath = adapter.getContextPath(servletContext);
                    String requestURI = adapter.getRequestURI(httpServletRequest);
                    servletPath = servletTransactionHelper.normalizeServletPath(requestURI, contextPath, servletPath, pathInfo);
                }

                servletTransactionHelper.onAfter(
                    transaction, t == null ? t2 : t,
                    adapter.isCommitted(httpServletResponse),
                    adapter.getStatus(httpServletResponse),
                    overrideStatusCodeOnThrowable,
                    adapter.getMethod(httpServletRequest),
                    parameterMap,
                    servletPath,
                    pathInfo,
                    contentTypeHeader,
                    true);
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

    private static boolean areNotEqual(@Nullable Object first, @Nullable Object second) {
        if (first == null) {
            return second != null;
        } else {
            return !first.equals(second);
        }
    }

}
