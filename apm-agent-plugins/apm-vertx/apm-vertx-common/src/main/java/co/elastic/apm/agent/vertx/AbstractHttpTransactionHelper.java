/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.vertx;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static co.elastic.apm.agent.configuration.CoreConfiguration.EventType.OFF;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_DEFAULT;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_LOW_LEVEL_FRAMEWORK;

public abstract class AbstractHttpTransactionHelper {
    private static final Logger logger = LoggerFactory.getLogger(AbstractHttpTransactionHelper.class);

    private static final String CONTENT_TYPE_FROM_URLENCODED = "application/x-www-form-urlencoded";
    private static final Set<String> METHODS_WITH_BODY = new HashSet<>(Arrays.asList("POST", "PUT", "PATCH", "DELETE"));
    private static final WildcardMatcher ENDS_WITH_JSP = WildcardMatcher.valueOf("*.jsp");


    protected static final String CONTENT_TYPE_HEADER = "Content-Type";
    protected static final String USER_AGENT_HEADER = "User-Agent";

    protected final Tracer tracer;
    protected final WebConfiguration webConfiguration;
    protected final CoreConfiguration coreConfiguration;

    protected AbstractHttpTransactionHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        webConfiguration = tracer.getConfig(WebConfiguration.class);
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
    }

    protected void startCaptureBody(Transaction transaction, String method, @Nullable String contentTypeHeader) {
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

    protected boolean hasBody(@Nullable String contentTypeHeader, String method) {
        return METHODS_WITH_BODY.contains(method) && contentTypeHeader != null;
    }

    public void applyDefaultTransactionName(String method, String pathFirstPart, @Nullable String pathSecondPart, Transaction transaction, int priorityOffset) {
        // JSPs don't contain path params and the name is more telling than the generated servlet class
        if (webConfiguration.isUsePathAsName() || ENDS_WITH_JSP.matches(pathFirstPart, pathSecondPart)) {
            // should override ServletName#doGet
            StringBuilder transactionName = transaction.getAndOverrideName(PRIO_LOW_LEVEL_FRAMEWORK + 1 + priorityOffset);
            if (transactionName != null) {
                WildcardMatcher groupMatcher = WildcardMatcher.anyMatch(webConfiguration.getUrlGroups(), pathFirstPart, pathSecondPart);
                if (groupMatcher != null) {
                    transactionName.append(method).append(' ').append(groupMatcher.toString());
                } else {
                    transactionName.append(method).append(' ').append(pathFirstPart);
                    if (pathSecondPart != null) {
                        transactionName.append(pathSecondPart);
                    }
                }
            }
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
    protected void fillRequestParameters(Transaction transaction, String method, @Nullable Map<String, String[]> parameterMap, @Nullable String contentTypeHeader) {
        Request request = transaction.getContext().getRequest();
        if (hasBody(contentTypeHeader, method)) {
            if (coreConfiguration.getCaptureBody() != OFF && parameterMap != null) {
                captureParameters(request, parameterMap, contentTypeHeader);
            }
        }
    }

    private void captureParameters(Request request, Map<String, String[]> params, @Nullable String contentTypeHeader) {
        if (contentTypeHeader != null && contentTypeHeader.startsWith(CONTENT_TYPE_FROM_URLENCODED)) {
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                request.addFormUrlEncodedParameters(param.getKey(), param.getValue());
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

    protected void fillResponse(Response response, @Nullable Boolean committed, int status) {
        response.withFinished(true);
        if (committed != null) {
            response.withHeadersSent(committed);
        }
        response.withStatusCode(status);
    }

    protected void fillRequest(Request request, String protocol, String method, boolean secure, @Nullable String scheme, @Nullable String serverName,
                               int serverPort, String requestURI, @Nullable String queryString, @Nullable String remoteAddr) {
        fillRequest(request, protocol, method, secure, remoteAddr);

        fillUrlRelatedFields(request, scheme, serverName, serverPort, requestURI, queryString);
    }

    protected void fillRequest(Request request, String protocol, String method, boolean secure, @Nullable String remoteAddr) {
        request.withHttpVersion(getHttpVersion(protocol));
        request.withMethod(method);

        request.getSocket()
            .withEncrypted(secure)
            .withRemoteAddress(remoteAddr);
    }

    protected void fillUrlRelatedFields(Request request, @Nullable String scheme, @Nullable String serverName, int serverPort, String requestURI, @Nullable String queryString) {
        request.getUrl().resetState();
        request.getUrl()
            .withProtocol(scheme)
            .withHostname(serverName)
            .withPort(serverPort)
            .withPathname(requestURI)
            .withSearch(queryString);

        fillFullUrl(request.getUrl(), scheme, serverPort, serverName, requestURI, queryString);
    }

    // inspired by org.apache.catalina.connector.Request.getRequestURL
    protected void fillFullUrl(Url url, @Nullable String scheme, int port, @Nullable String serverName, @Nullable String requestURI, @Nullable String queryString) {
        // using a StringBuilder to avoid allocations when constructing the full URL
        final StringBuilder fullUrl = url.getFull();

        if (serverName != null) {
            if (scheme != null) {
                fullUrl.append(scheme);
                fullUrl.append("://");
            }
            fullUrl.append(serverName);
            if (port < 0) {
                port = 80; // Work around java.net.URL bug
            }
        }
        if (port > 0) {
            if (scheme == null || (scheme.equals("http") && (port != 80)) ||
                (scheme.equals("https") && (port != 443))) {
                fullUrl.append(':');
                fullUrl.append(port);
            }
        }

        if (requestURI != null) {
            fullUrl.append(requestURI);
        }

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

    public boolean isCaptureHeaders() {
        return coreConfiguration.isCaptureHeaders();
    }

    protected boolean isExcluded(String servletPath, @Nullable String pathInfo, @Nullable String userAgentHeader) {
        final WildcardMatcher excludeUrlMatcher = WildcardMatcher.anyMatch(webConfiguration.getIgnoreUrls(), servletPath, pathInfo);
        if (excludeUrlMatcher != null && logger.isDebugEnabled()) {
            logger.debug("Not tracing this request as the URL {}{} is ignored by the matcher {}",
                servletPath, Objects.toString(pathInfo, ""), excludeUrlMatcher);
        }
        final WildcardMatcher excludeAgentMatcher = userAgentHeader != null ? WildcardMatcher.anyMatch(webConfiguration.getIgnoreUserAgents(), userAgentHeader) : null;
        if (excludeAgentMatcher != null) {
            logger.debug("Not tracing this request as the User-Agent {} is ignored by the matcher {}",
                userAgentHeader, excludeAgentMatcher);
        }
        boolean isExcluded = excludeUrlMatcher != null || excludeAgentMatcher != null;
        if (!isExcluded && logger.isTraceEnabled()) {
            logger.trace("No matcher found for excluding this request with servlet-path: {}, path-info: {} and User-Agent: {}",
                servletPath, pathInfo, userAgentHeader);
        }
        return isExcluded;
    }
}
