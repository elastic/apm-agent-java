/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.web.ClientIpUtils;
import co.elastic.apm.agent.web.ResultUtil;
import co.elastic.apm.agent.web.WebConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static co.elastic.apm.agent.web.WebConfiguration.EventType.OFF;

/**
 * This class must not import classes from {@code javax.servlet} due to class loader issues.
 * See https://github.com/raphw/byte-buddy/issues/465 for more information.
 */
@VisibleForAdvice
public class ServletTransactionHelper {

    @VisibleForAdvice
    public static final String TRANSACTION_ATTRIBUTE = ServletApiAdvice.class.getName() + ".transaction";
    @VisibleForAdvice
    public static final String ASYNC_ATTRIBUTE = ServletApiAdvice.class.getName() + ".async";

    private final Logger logger = LoggerFactory.getLogger(ServletTransactionHelper.class);

    private final Set<String> METHODS_WITH_BODY = new HashSet<>(Arrays.asList("POST", "PUT", "PATCH", "DELETE"));
    private final ElasticApmTracer tracer;
    private final CoreConfiguration coreConfiguration;
    private final WebConfiguration webConfiguration;

    @VisibleForAdvice
    public ServletTransactionHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        this.webConfiguration = tracer.getConfig(WebConfiguration.class);
    }

    /*
     * As much of the request information as possible should be set before the request processing starts.
     *
     * That way, when recording an error,
     * we can copy the transaction context to the error context.
     *
     * This has the advantage that we don't have to create the context for the error again.
     * As creating the context is framework specific,
     * this also means less effort when adding support for new frameworks,
     * because the creating the context is handled in one central place.
     *
     * Furthermore, it is not trivial to create an error context at an arbitrary location
     * (when the user calls ElasticApm.captureException()),
     * as we don't necessarily have access to the framework's request and response objects.
     *
     * Additionally, we only have access to the classes of the instrumented classes inside advice methods.
     *
     * Currently, there is no configuration option to disable tracing but to still enable error tracking.
     * But even when introducing that, the approach of copying the transaction context can still work.
     * We will then capture the transaction but not report it.
     * As the capturing of the transaction is garbage free, this should not add a significant overhead.
     * Also, this setting would be rather niche, as we are a APM solution after all.
     */
    @Nullable
    @VisibleForAdvice
    public Transaction onBefore(String servletPath, @Nullable String pathInfo,
                                @Nullable String userAgentHeader,
                                @Nullable String traceContextHeader) {
        if (coreConfiguration.isActive() &&
            // only create a transaction if there is not already one
            tracer.currentTransaction() == null &&
            !isExcluded(servletPath, pathInfo, userAgentHeader)) {
            return tracer.startTransaction(TraceContext.fromTraceparentHeader(), traceContextHeader).activate();
        } else {
            return null;
        }
    }

    @VisibleForAdvice
    public void fillRequestContext(Transaction transaction, String protocol, String method, boolean secure,
                                   String scheme, String serverName, int serverPort, String requestURI, String queryString,
                                   String remoteAddr) {

        final Request request = transaction.getContext().getRequest();
        fillRequest(request, protocol, method, secure, scheme, serverName, serverPort, requestURI, queryString, remoteAddr);
    }

    @VisibleForAdvice
    public static void setUsernameIfUnset(@Nullable String userName, TransactionContext context) {
        // only set username if not manually set
        if (context.getUser().getUsername() == null) {
            context.getUser().withUsername(userName);
        }
    }

    @VisibleForAdvice
    public void onAfter(Transaction transaction, @Nullable Throwable exception, boolean committed, int status, String method,
                        @Nullable Map<String, String[]> parameterMap, String servletPath, @Nullable String pathInfo, @Nullable String contentTypeHeader) {
        try {
            fillRequestParameters(transaction, method, parameterMap, contentTypeHeader);
            if(exception != null && status == 200) {
                // Probably shouldn't be 200 but 5XX, but we are going to miss this...
                status = 500;
            }
            fillResponse(transaction.getContext().getResponse(), committed, status);
            transaction.withResult(ResultUtil.getResultByHttpStatus(status));
            transaction.withType("request");
            if (transaction.getName().length() == 0) {
                applyDefaultTransactionName(method, servletPath, pathInfo, transaction.getName());
            }
            if (exception != null) {
                transaction.captureException(exception);
            }
        } catch (RuntimeException e) {
            // in case we screwed up, don't bring down the monitored application with us
            logger.warn("Exception while capturing Elastic APM transaction", e);
        }
        if (tracer.getActive() == transaction) {
            // when calling javax.servlet.AsyncContext#start, the transaction is not activated,
            // as neither javax.servlet.Filter.doFilter nor javax.servlet.AsyncListener.onStartAsync will be invoked
            transaction.deactivate();
        }
        transaction.end();
    }

    void applyDefaultTransactionName(String method, String servletPath, @Nullable String pathInfo, StringBuilder transactionName) {
        if (webConfiguration.isUsePathAsName()) {
            WildcardMatcher groupMatcher = WildcardMatcher.anyMatch(webConfiguration.getUrlGroups(), servletPath, pathInfo);
            if (groupMatcher != null) {
                transactionName.append(method).append(' ').append(groupMatcher.toString());
            } else {
                transactionName.append(method).append(' ').append(servletPath);
                if (pathInfo != null) {
                    transactionName.append(pathInfo);
                }
            }
        } else {
            transactionName.append(method);
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
            if (webConfiguration.getCaptureBody() != OFF && parameterMap != null) {
                captureBody(request, parameterMap, contentTypeHeader);
            } else {
                request.redactBody();
            }
        }
    }

    @VisibleForAdvice
    public boolean captureParameters(String method, @Nullable String contentTypeHeader) {
        return contentTypeHeader != null
            && contentTypeHeader.startsWith("application/x-www-form-urlencoded")
            && hasBody(contentTypeHeader, method)
            && webConfiguration.getCaptureBody() != OFF;
    }

    private boolean isExcluded(String servletPath, @Nullable String pathInfo, @Nullable String userAgentHeader) {
        final WildcardMatcher excludeUrlMatcher = WildcardMatcher.anyMatch(webConfiguration.getIgnoreUrls(), servletPath, pathInfo);
        if (excludeUrlMatcher != null) {
            logger.debug("Not tracing this request as the URL {}{} is ignored by the matcher {}",
                servletPath, Objects.toString(pathInfo, ""), excludeUrlMatcher);
        }
        final WildcardMatcher excludeAgentMatcher = userAgentHeader != null ? WildcardMatcher.anyMatch(webConfiguration.getIgnoreUserAgents(), userAgentHeader) : null;
        if (excludeAgentMatcher != null) {
            logger.debug("Not tracing this request as the User-Agent {} is ignored by the matcher {}",
                userAgentHeader, excludeAgentMatcher);
        }
        return excludeUrlMatcher != null || excludeAgentMatcher != null;
    }

    private void fillResponse(Response response, boolean committed, int status) {
        response.withFinished(true);
        response.withHeadersSent(committed);
        response.withStatusCode(status);
    }

    private void fillRequest(Request request, String protocol, String method, boolean secure, String scheme, String serverName,
                             int serverPort, String requestURI, String queryString,
                             String remoteAddr) {

        request.withHttpVersion(getHttpVersion(protocol));
        request.withMethod(method);

        request.getSocket()
            .withEncrypted(secure)
            .withRemoteAddress(ClientIpUtils.getRealIp(request.getHeaders(), remoteAddr));

        request.getUrl()
            .withProtocol(scheme)
            .withHostname(serverName)
            .withPort(serverPort)
            .withPathname(requestURI)
            .withSearch(queryString);

        fillFullUrl(request.getUrl(), scheme, serverPort, serverName, requestURI, queryString);
    }

    private boolean hasBody(@Nullable String contentTypeHeader, String method) {
        return METHODS_WITH_BODY.contains(method) && contentTypeHeader != null;
    }

    private void captureBody(Request request, Map<String, String[]> params, @Nullable String contentTypeHeader) {
        if (contentTypeHeader != null && contentTypeHeader.startsWith("application/x-www-form-urlencoded")) {
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                request.addFormUrlEncodedParameters(param.getKey(), param.getValue());
            }
        } else {
            // this content-type is not supported (yet)
            request.redactBody();
        }
    }

    // inspired by org.apache.catalina.connector.Request.getRequestURL
    private void fillFullUrl(Url url, String scheme, int port, String serverName, String requestURI, @Nullable String queryString) {
        // using a StringBuilder to avoid allocations when constructing the full URL
        final StringBuilder fullUrl = url.getFull();
        if (port < 0) {
            port = 80; // Work around java.net.URL bug
        }

        fullUrl.append(scheme);
        fullUrl.append("://");
        fullUrl.append(serverName);
        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            fullUrl.append(':');
            fullUrl.append(port);
        }
        fullUrl.append(requestURI);
        if (queryString != null) {
            fullUrl.append('?').append(queryString);
        }
    }

    private String getHttpVersion(String protocol) {
        // don't allocate new strings in the common cases
        switch (protocol) {
            case "HTTP/1.0":
                return "1.0";
            case "HTTP/1.1":
                return "1.1";
            case "HTTP/2.0":
                return "2.0";
            default:
                return protocol.replace("HTTP/", "");
        }
    }

    @VisibleForAdvice
    public static void setTransactionNameByServletClass(String method, @Nullable Class<?> servletClass, StringBuilder transactionName) {
        if (servletClass == null || transactionName.length() > 0) {
            return;
        }
        String servletClassName = servletClass.getName();
        transactionName.append(servletClassName, servletClassName.lastIndexOf('.') + 1, servletClassName.length());
        transactionName.append('#');
        switch (method) {
            case "DELETE":
                transactionName.append("doDelete");
                break;
            case "HEAD":
                transactionName.append("doHead");
                break;
            case "GET":
                transactionName.append("doGet");
                break;
            case "OPTIONS":
                transactionName.append("doOptions");
                break;
            case "POST":
                transactionName.append("doPost");
                break;
            case "PUT":
                transactionName.append("doPut");
                break;
            case "TRACE":
                transactionName.append("doTrace");
                break;
            default:
                transactionName.append(method);
        }
    }

    public boolean isCaptureHeaders() {
        return webConfiguration.isCaptureHeaders();
    }
}
