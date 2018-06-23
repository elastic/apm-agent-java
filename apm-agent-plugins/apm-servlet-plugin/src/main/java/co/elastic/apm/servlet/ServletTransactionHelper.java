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
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.context.Response;
import co.elastic.apm.impl.context.Url;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.matcher.WildcardMatcher;
import co.elastic.apm.util.PotentiallyMultiValuedMap;
import co.elastic.apm.web.ClientIpUtils;
import co.elastic.apm.web.ResultUtil;
import co.elastic.apm.web.WebConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static co.elastic.apm.web.WebConfiguration.EventType.OFF;

/**
 * This class must not import classes from {@code javax.servlet} due to class loader issues.
 * See https://github.com/raphw/byte-buddy/issues/465 for more information.
 */
@VisibleForAdvice
public class ServletTransactionHelper {

    private final Logger logger = LoggerFactory.getLogger(ServletTransactionHelper.class);

    private final Set<String> METHODS_WITH_BODY = new HashSet<>(Arrays.asList("POST", "PUT", "PATCH", "DELETE"));
    private final ElasticApmTracer tracer;
    private final CoreConfiguration coreConfiguration;
    private final WebConfiguration webConfiguration;

    ServletTransactionHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        this.webConfiguration = tracer.getConfig(WebConfiguration.class);
    }

    @Nullable
    @VisibleForAdvice
    public Transaction onBefore(String servletPath, String pathInfo, String requestURI,
                                @Nullable String userAgentHeader,
                                @Nullable String traceContextHeader) {
        if (coreConfiguration.isActive() &&
            // only create a transaction if there is not already one
            tracer.currentTransaction() == null &&
            !isExcluded(servletPath, pathInfo, requestURI, userAgentHeader)) {
            return tracer.startTransaction(traceContextHeader);
        } else {
            return null;
        }
    }

    /*
     * filling the transaction after the request has been processed is safer
     * as reading the parameters could potentially decode them in the wrong encoding
     * or trigger exceptions,
     * for example when the amount of query parameters is longer than the application server allows
     * in that case, we rather want that the agent looks like the cause for this
     */
    @VisibleForAdvice
    public void onAfter(Transaction transaction, @Nullable Exception exception,
                        @Nullable String userName, String protocol, String method, boolean secure, String scheme, String serverName,
                        int serverPort, String requestURI, String queryString, Map<String, String[]> parameterMap, String remoteAddr,
                        StringBuffer requestURL, boolean committed, int status) {
        try {
            Context context = transaction.getContext();
            final Request request = transaction.getContext().getRequest();
            fillRequest(request, protocol, method, secure, scheme, serverName, serverPort, requestURI, queryString, parameterMap,
                remoteAddr, requestURL);

            fillResponse(context.getResponse(), committed, status);
            // only set username if not manually set
            if (context.getUser().getUsername() == null) {
                context.getUser().withUsername(userName);
            }

            // the HTTP method is not a good transaction name, but better than none...
            if (transaction.getName().length() == 0) {
                transaction.withName(method);
            }
            transaction.withResult(ResultUtil.getResultByHttpStatus(status));
            transaction.withType("request");
            if (exception != null) {
                tracer.captureException(exception);
            }
        } catch (RuntimeException e) {
            // in case we screwed up, don't bring down the monitored application with us
            logger.warn("Exception while capturing Elastic APM transaction", e);
        }
        transaction.end();
    }

    private boolean isExcluded(String servletPath, String pathInfo, String requestURI, @Nullable String userAgentHeader) {
        boolean excludeUrl = WildcardMatcher.anyMatch(webConfiguration.getIgnoreUrls(), servletPath, pathInfo);
        if (excludeUrl) {
            logger.debug("Not tracing this request as the URL {} is ignored by one of the matchers",
                requestURI, webConfiguration.getIgnoreUrls());
        }
        boolean excludeAgent = userAgentHeader != null && WildcardMatcher.anyMatch(webConfiguration.getIgnoreUserAgents(), userAgentHeader);
        if (excludeAgent) {
            logger.debug("Not tracing this request as the User-Agent {} is ignored by one of the matchers",
                userAgentHeader, webConfiguration.getIgnoreUserAgents());
        }
        return excludeUrl || excludeAgent;
    }

    private void fillResponse(Response response, boolean committed, int status) {
        response.withFinished(true);
        response.withHeadersSent(committed);
        response.withStatusCode(status);
    }

    private void fillRequest(Request request, String protocol, String method, boolean secure,
                             String scheme, String serverName, int serverPort, String requestURI, String queryString,
                             Map<String, String[]> parameterMap, String remoteAddr, StringBuffer requestURL) {
        final WebConfiguration.EventType eventType = webConfiguration.getCaptureBody();

        if (hasBody(request.getHeaders(), method)) {
            if (eventType != OFF) {
                captureBody(request, parameterMap);
            } else {
                request.redactBody();
            }
        }
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

        fillFullUrl(request.getUrl(), queryString, requestURL);
    }

    private boolean hasBody(PotentiallyMultiValuedMap headers, String method) {
        return METHODS_WITH_BODY.contains(method) && headers.containsIgnoreCase("Content-Type");
    }

    private void captureBody(Request request, Map<String, String[]> params) {
        String contentTypeHeader = request.getHeaders().getFirst("Content-Type");
        if (contentTypeHeader != null && contentTypeHeader.startsWith("application/x-www-form-urlencoded")) {
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                request.addFormUrlEncodedParameters(param.getKey(), param.getValue());
            }
        } else {
            // this content-type is not supported (yet)
            request.redactBody();
        }
    }

    private void fillFullUrl(Url url, @Nullable String queryString, StringBuffer requestURL) {
        // using a StringBuilder to avoid allocations when constructing the full URL
        final StringBuilder fullUrl = url.getFull();
        if (queryString != null) {
            fullUrl.ensureCapacity(requestURL.length() + 1 + queryString.length());
            fullUrl.append(requestURL).append('?').append(queryString);
        } else {
            fullUrl.ensureCapacity(requestURL.length());
            fullUrl.append(requestURL);
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

}
