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
import co.elastic.apm.agent.sdk.state.GlobalThreadLocal;
import co.elastic.apm.agent.util.TransactionNameUtils;

import javax.annotation.Nullable;
import java.security.Principal;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_LOW_LEVEL_FRAMEWORK;
import static co.elastic.apm.agent.servlet.ServletTransactionHelper.TRANSACTION_ATTRIBUTE;
import static co.elastic.apm.agent.servlet.ServletTransactionHelper.determineServiceName;

public abstract class ServletApiAdvice {

    private static final String FRAMEWORK_NAME = "Servlet API";
    static final String SPAN_TYPE = "servlet";
    static final String SPAN_SUBTYPE = "request-dispatcher";
    private static final ServletTransactionHelper servletTransactionHelper;

    static {
        servletTransactionHelper = new ServletTransactionHelper(GlobalTracer.requireTracerImpl());
    }

    private static final GlobalThreadLocal<Boolean> excluded = GlobalThreadLocal.get(ServletApiAdvice.class, "excluded");
    private static final GlobalThreadLocal<Object> servletPathTL = GlobalThreadLocal.get(ServletApiAdvice.class, "servletPath");
    private static final GlobalThreadLocal<Object> pathInfoTL = GlobalThreadLocal.get(ServletApiAdvice.class, "pathInfo");

    private static final List<String> requestExceptionAttributes = Arrays.asList("javax.servlet.error.exception", "jakarta.servlet.error.exception", "exception", "org.springframework.web.servlet.DispatcherServlet.EXCEPTION", "co.elastic.apm.exception");

    @Nullable
    public static <ServletRequest, ServletResponse, HttpServletRequest, HttpServletResponse, ServletContext> Object onServletEnter(ServletRequest servletRequest, ServletHelper<ServletRequest, ServletResponse, HttpServletRequest, HttpServletResponse, ServletContext> helper) {
        ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            return null;
        }
        AbstractSpan<?> ret = null;
        // re-activate transactions for async requests
        final Transaction transactionAttr = (Transaction) helper.getAttribute(servletRequest, TRANSACTION_ATTRIBUTE);
        if (tracer.currentTransaction() == null && transactionAttr != null) {
            return transactionAttr.activateInScope();
        }

        if (!tracer.isRunning() || !helper.isHttpServletRequest(servletRequest)) {
            return null;
        }

        final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);

        if (helper.isRequestDispatcherType(servletRequest)) {
            if (Boolean.TRUE == excluded.get()) {
                return null;
            }

            ServletContext servletContext = helper.getServletContext(servletRequest);
            if (servletContext != null) {
                ClassLoader servletCL = helper.getClassloader(servletContext);
                // this makes sure service name discovery also works when attaching at runtime
                determineServiceName(helper.getServletContextName(servletContext), servletCL, helper.getContextPath(servletContext));
            }

            Transaction transaction = helper.createAndActivateTransaction(httpServletRequest);

            if (transaction == null) {
                // if the httpServletRequest is excluded, avoid matching all exclude patterns again on each filter invocation
                excluded.set(Boolean.TRUE);
                return null;
            }

            final Request req = transaction.getContext().getRequest();
            if (transaction.isSampled() && coreConfig.isCaptureHeaders()) {
                helper.handleCookies(req, httpServletRequest);

                final Enumeration<String> headerNames = helper.getRequestHeaderNames(httpServletRequest);
                if (headerNames != null) {
                    while (headerNames.hasMoreElements()) {
                        final String headerName = headerNames.nextElement();
                        req.addHeader(headerName, helper.getRequestHeaders(httpServletRequest, headerName));
                    }
                }
            }
            transaction.setFrameworkName(FRAMEWORK_NAME);

            servletTransactionHelper.fillRequestContext(transaction, helper.getProtocol(httpServletRequest), helper.getMethod(httpServletRequest), helper.isSecure(httpServletRequest),
                helper.getScheme(httpServletRequest), helper.getServerName(httpServletRequest), helper.getServerPort(httpServletRequest), helper.getRequestURI(httpServletRequest), helper.getQueryString(httpServletRequest),
                helper.getRemoteAddr(httpServletRequest), helper.getHeader(httpServletRequest, "Content-Type"));

            ret = transaction;
        } else if (!helper.isAsyncDispatcherType(servletRequest) &&
            !coreConfig.getDisabledInstrumentations().contains(Constants.SERVLET_API_DISPATCH)) {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent != null) {
                Object servletPath = null;
                Object pathInfo = null;
                RequestDispatcherSpanType spanType = null;
                if (helper.isForwardDispatcherType(servletRequest)) {
                    spanType = RequestDispatcherSpanType.FORWARD;
                    servletPath = helper.getServletPath(httpServletRequest);
                    pathInfo = helper.getPathInfo(httpServletRequest);
                } else if (helper.isIncludeDispatcherType(servletRequest)) {
                    spanType = RequestDispatcherSpanType.INCLUDE;
                    servletPath = helper.getIncludeServletPathAttribute(httpServletRequest);
                    pathInfo = helper.getIncludePathInfoAttribute(httpServletRequest);
                } else if (helper.isErrorDispatcherType(servletRequest)) {
                    spanType = RequestDispatcherSpanType.ERROR;
                    servletPath = helper.getServletPath(httpServletRequest);
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

    public static <ServletRequest, ServletResponse, HttpServletRequest, HttpServletResponse, ServletContext> void onExitServlet(ServletRequest servletRequest,
                                                                       ServletResponse servletResponse,
                                                                       @Nullable Object transactionOrScopeOrSpan,
                                                                       @Nullable Throwable t,
                                                                       Object thiz,
                                                                       ServletHelper<ServletRequest, ServletResponse, HttpServletRequest, HttpServletResponse, ServletContext> helper) {
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

        excluded.clear();
        if (scope != null) {
            scope.close();
        }
        if (helper.isInstanceOfHttpServlet(thiz) && helper.isHttpServletRequest(servletRequest)) {
            Transaction currentTransaction = tracer.currentTransaction();
            if (currentTransaction != null) {
                final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
                TransactionNameUtils.setTransactionNameByServletClass(helper.getMethod(httpServletRequest), thiz.getClass(), currentTransaction.getAndOverrideName(PRIO_LOW_LEVEL_FRAMEWORK));
                final Principal userPrincipal = helper.getUserPrincipal(httpServletRequest);
                ServletTransactionHelper.setUsernameIfUnset(userPrincipal != null ? userPrincipal.getName() : null, currentTransaction.getContext());
            }
        }
        if (transaction != null &&
            helper.isHttpServletRequest(servletRequest) &&
            helper.isHttpServletResponse(servletResponse)) {

            final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
            if (helper.getHttpAttribute(httpServletRequest, ServletTransactionHelper.ASYNC_ATTRIBUTE) != null) {
                // HttpServletRequest.startAsync was invoked on this httpServletRequest.
                // The transaction should be handled from now on by the other thread committing the response
                transaction.deactivate();
            } else {
                // this is not an async httpServletRequest, so we can end the transaction immediately
                final HttpServletResponse response = (HttpServletResponse) servletResponse;
                if (transaction.isSampled() && tracer.getConfig(CoreConfiguration.class).isCaptureHeaders()) {
                    final Response resp = transaction.getContext().getResponse();
                    for (String headerName : helper.getHeaderNames(response)) {
                        resp.addHeader(headerName, helper.getHeaders(response, headerName));
                    }
                }
                // httpServletRequest.getParameterMap() may allocate a new map, depending on the servlet container implementation
                // so only call this method if necessary
                final String contentTypeHeader = helper.getHeader(httpServletRequest, "Content-Type");
                final Map<String, String[]> parameterMap;
                if (transaction.isSampled() && servletTransactionHelper.captureParameters(helper.getMethod(httpServletRequest), contentTypeHeader)) {
                    parameterMap = helper.getParameterMap(httpServletRequest);
                } else {
                    parameterMap = null;
                }

                Throwable t2 = null;
                boolean overrideStatusCodeOnThrowable = true;
                if (t == null) {
                    final int size = requestExceptionAttributes.size();
                    for (int i = 0; i < size; i++) {
                        String attributeName = requestExceptionAttributes.get(i);
                        Object throwable = helper.getHttpAttribute(httpServletRequest, attributeName);
                        if (throwable instanceof Throwable) {
                            t2 = (Throwable) throwable;
                            if (!attributeName.equals("javax.servlet.error.exception") && !attributeName.equals("jakarta.servlet.error.exception")) {
                                overrideStatusCodeOnThrowable = false;
                            }
                            break;
                        }
                    }
                }

                servletTransactionHelper.onAfter(transaction, t == null ? t2 : t, helper.isCommitted(response), helper.getStatus(response),
                    overrideStatusCodeOnThrowable, helper.getMethod(httpServletRequest), parameterMap, helper.getServletPath(httpServletRequest),
                    helper.getPathInfo(httpServletRequest), contentTypeHeader, true
                );
            }
        }
        if (span != null) {
            servletPathTL.clear();
            pathInfoTL.clear();
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
