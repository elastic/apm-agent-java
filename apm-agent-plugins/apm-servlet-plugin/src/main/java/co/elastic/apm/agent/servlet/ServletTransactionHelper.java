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
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.util.TransactionNameUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static co.elastic.apm.agent.configuration.CoreConfiguration.EventType.OFF;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_DEFAULT;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_LOW_LEVEL_FRAMEWORK;
import static co.elastic.apm.agent.servlet.ServletGlobalState.nameInitialized;

public class ServletTransactionHelper {

    public static final String TRANSACTION_ATTRIBUTE = ServletApiAdvice.class.getName() + ".transaction";

    public static final String ASYNC_ATTRIBUTE = ServletApiAdvice.class.getName() + ".async";

    private static final String CONTENT_TYPE_FROM_URLENCODED = "application/x-www-form-urlencoded";
    private static final WildcardMatcher ENDS_WITH_JSP = WildcardMatcher.valueOf("*.jsp");

    private static final Logger logger = LoggerFactory.getLogger(ServletTransactionHelper.class);

    private final Set<String> METHODS_WITH_BODY = new HashSet<>(Arrays.asList("POST", "PUT", "PATCH", "DELETE"));
    private final CoreConfiguration coreConfiguration;
    private final WebConfiguration webConfiguration;

    public ServletTransactionHelper(ElasticApmTracer tracer) {
        this.coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        this.webConfiguration = tracer.getConfig(WebConfiguration.class);
    }

    public static void determineServiceName(@Nullable String servletContextName, @Nullable ClassLoader servletContextClassLoader, @Nullable String contextPath) {
        if (servletContextClassLoader == null || nameInitialized.putIfAbsent(servletContextClassLoader, Boolean.TRUE) != null) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Inferring service name for class loader [{}] based on servlet context path `{}` and request context path `{}`",
                servletContextClassLoader,
                servletContextName,
                contextPath
            );
        }

        @Nullable
        String serviceName = servletContextName;
        if ("application".equals(serviceName) || "".equals(serviceName) || "/".equals(serviceName)) {
            // payara returns an empty string as opposed to null
            // spring applications which did not set spring.application.name have application as the default
            // jetty returns context path when no display name is set, which could be the root context of "/"
            // this is a worse default than the one we would otherwise choose
            serviceName = null;
        }
        if (serviceName == null && contextPath != null && !contextPath.isEmpty()) {
            // remove leading slash
            serviceName = contextPath.substring(1);
        }
        if (serviceName != null) {
            GlobalTracer.get().overrideServiceNameForClassLoader(servletContextClassLoader, serviceName);
        }
    }

    public void fillRequestContext(Transaction transaction, String protocol, String method, boolean secure,
                                   String scheme, String serverName, int serverPort, String requestURI, String queryString,
                                   String remoteAddr, @Nullable String contentTypeHeader) {

        startCaptureBody(transaction, method, contentTypeHeader);

        // fill request
        Request request = transaction.getContext().getRequest();

        request.withHttpVersion(protocol)
            .withMethod(method);

        request.getSocket()
            .withRemoteAddress(remoteAddr);

        request.getUrl()
            .withProtocol(scheme)
            .withHostname(serverName)
            .withPort(serverPort)
            .withPathname(requestURI)
            .withSearch(queryString);

    }

    private void startCaptureBody(Transaction transaction, String method, @Nullable String contentTypeHeader) {
        Request request = transaction.getContext().getRequest();
        if (hasBody(contentTypeHeader, method)) {
            if (coreConfiguration.getCaptureBody() != OFF
                && contentTypeHeader != null
                // form parameters are recorded via ServletRequest.getParameterMap
                // as the container might not call ServletRequest.getInputStream
                && !contentTypeHeader.startsWith(CONTENT_TYPE_FROM_URLENCODED)
                && WildcardMatcher.isAnyMatch(webConfiguration.getCaptureContentTypes(), contentTypeHeader)) {
                request.withBodyBuffer();
            } else {
                request.redactBody();
                if (coreConfiguration.getCaptureBody() == OFF) {
                    logger.debug("Not capturing Request body because the capture_body config option is OFF");
                }
                if (contentTypeHeader == null) {
                    logger.debug("Not capturing request body because couldn't find Content-Type header");
                } else if (!contentTypeHeader.startsWith(CONTENT_TYPE_FROM_URLENCODED)) {
                    logger.debug("Not capturing body for content type \"{}\". Consider updating the capture_body_content_types " +
                        "configuration option.", contentTypeHeader);
                }
            }
        }
    }

    public static void setUsernameIfUnset(@Nullable String userName, TransactionContext context) {
        // only set username if not manually set
        if (context.getUser().getUsername() == null) {
            context.getUser().withUsername(userName);
        }
    }

    public void onAfter(Transaction transaction, @Nullable Throwable exception, boolean committed, int status,
                        boolean overrideStatusCodeOnThrowable, String method, @Nullable Map<String, String[]> parameterMap,
                        @Nullable String servletPath, @Nullable String pathInfo, @Nullable String contentTypeHeader, boolean deactivate) {
        if (servletPath == null) {
            // the servlet path is specified as non-null but WebLogic does return null...
            servletPath = "";
        }
        try {
            // thrown the first time a JSP is invoked in order to register it
            if (exception != null && "weblogic.servlet.jsp.AddToMapException".equals(exception.getClass().getName())) {
                transaction.ignoreTransaction();
            } else {
                doOnAfter(transaction, exception, committed, status, overrideStatusCodeOnThrowable, method,
                    parameterMap, servletPath, pathInfo, contentTypeHeader);
            }
        } catch (RuntimeException e) {
            // in case we screwed up, don't bring down the monitored application with us
            logger.warn("Exception while capturing Elastic APM transaction", e);
        }
        if (deactivate) {
            transaction.deactivate();
        }
        transaction.end();
    }

    private void doOnAfter(Transaction transaction, @Nullable Throwable exception, boolean committed, int status,
                           boolean overrideStatusCodeOnThrowable, String method, @Nullable Map<String, String[]> parameterMap,
                           String servletPath, @Nullable String pathInfo, @Nullable String contentTypeHeader) {
        fillRequestParameters(transaction, method, parameterMap, contentTypeHeader);
        if (exception != null && status == 200 && overrideStatusCodeOnThrowable) {
            // Probably shouldn't be 200 but 5XX, but we are going to miss this...
            status = 500;
        }
        fillResponse(transaction.getContext().getResponse(), committed, status);
        transaction.withResultIfUnset(ResultUtil.getResultByHttpStatus(status))
            .withType("request")
            .captureException(exception);
        applyDefaultTransactionName(method, servletPath, pathInfo, transaction);
    }

    void applyDefaultTransactionName(String method, String servletPath, @Nullable String pathInfo, Transaction transaction) {
        // JSPs don't contain path params and the name is more telling than the generated servlet class
        if (webConfiguration.isUsePathAsName() || ENDS_WITH_JSP.matches(servletPath, pathInfo)) {
            // should override ServletName#doGet
            TransactionNameUtils.setNameFromHttpRequestPath(method, servletPath, pathInfo, transaction.getAndOverrideName(PRIO_LOW_LEVEL_FRAMEWORK + 1), webConfiguration.getUrlGroups());
        } else {
            StringBuilder transactionName = transaction.getAndOverrideName(PRIO_DEFAULT);
            if (transactionName != null) {
                transactionName.append(method).append(" unknown route");
            }
        }
    }

    /*
     * Filling the parameter after the request has been processed is safer
     * as reading the parameters could potentially decode them in the wrong encoding
     * or trigger exceptions,
     * for example when the amount of query parameters is longer than the application server allows.
     * In that case, we rather not want that the agent looks like the cause for this.
     */
    private void fillRequestParameters(Transaction transaction, String method, @Nullable Map<String, String[]> parameterMap, @Nullable String contentTypeHeader) {
        Request request = transaction.getContext().getRequest();
        if (hasBody(contentTypeHeader, method)) {
            if (coreConfiguration.getCaptureBody() != OFF && parameterMap != null) {
                captureParameters(request, parameterMap, contentTypeHeader);
            }
        }
    }

    public boolean captureParameters(String method, @Nullable String contentTypeHeader) {
        return contentTypeHeader != null
            && contentTypeHeader.startsWith(CONTENT_TYPE_FROM_URLENCODED)
            && hasBody(contentTypeHeader, method)
            && coreConfiguration.getCaptureBody() != OFF
            && WildcardMatcher.isAnyMatch(webConfiguration.getCaptureContentTypes(), contentTypeHeader);
    }

    private void fillResponse(Response response, boolean committed, int status) {
        response.withFinished(true);
        response.withHeadersSent(committed);
        response.withStatusCode(status);
    }

    private boolean hasBody(@Nullable String contentTypeHeader, String method) {
        return METHODS_WITH_BODY.contains(method) && contentTypeHeader != null;
    }

    private void captureParameters(Request request, Map<String, String[]> params, @Nullable String contentTypeHeader) {
        if (contentTypeHeader != null && contentTypeHeader.startsWith(CONTENT_TYPE_FROM_URLENCODED)) {
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                request.addFormUrlEncodedParameters(param.getKey(), param.getValue());
            }
        }
    }

    public boolean isCaptureHeaders() {
        return coreConfiguration.isCaptureHeaders();
    }
}
