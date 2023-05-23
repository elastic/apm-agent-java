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
package co.elastic.apm.agent.vertx;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.httpserver.HttpServerHelper;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.tracer.metadata.Request;
import co.elastic.apm.agent.tracer.metadata.Response;
import co.elastic.apm.agent.util.TransactionNameUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static co.elastic.apm.agent.configuration.CoreConfiguration.EventType.OFF;
import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_DEFAULT;
import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_LOW_LEVEL_FRAMEWORK;

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

    protected final HttpServerHelper serverHelper;

    protected AbstractHttpTransactionHelper(Tracer tracer) {
        this.tracer = tracer;
        this.webConfiguration = tracer.getConfig(WebConfiguration.class);
        this.coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        this.serverHelper = new HttpServerHelper(webConfiguration);
    }

    protected void startCaptureBody(Transaction<?> transaction, String method, @Nullable String contentTypeHeader) {
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

    public void applyDefaultTransactionName(String method, String pathFirstPart, @Nullable String pathSecondPart, Transaction<?> transaction, int priorityOffset) {
        // JSPs don't contain path params and the name is more telling than the generated servlet class
        if (webConfiguration.isUsePathAsName() || ENDS_WITH_JSP.matches(pathFirstPart, pathSecondPart)) {
            // should override ServletName#doGet
            TransactionNameUtils.setNameFromHttpRequestPath(
                method,
                pathFirstPart,
                pathSecondPart,
                transaction.getAndOverrideName(PRIORITY_LOW_LEVEL_FRAMEWORK + 1 + priorityOffset),
                webConfiguration.getUrlGroups());
        } else {
            TransactionNameUtils.setNameUnknownRoute(method, transaction.getAndOverrideName(PRIORITY_DEFAULT));
        }
    }

    /*
     * Filling the parameter after the request has been processed is safer
     * as reading the parameters could potentially decode them in the wrong encoding
     * or trigger exceptions,
     * for example when the amount of query parameters is longer than the application server allows.
     * In that case, we rather not want that the agent looks like the cause for this.
     */
    protected void fillRequestParameters(Transaction<?> transaction, String method, @Nullable Map<String, String[]> parameterMap, @Nullable String contentTypeHeader) {
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

    protected void fillRequest(Request request, String protocol, String method, @Nullable String scheme, @Nullable String serverName,
                               int serverPort, String requestURI, @Nullable String queryString, @Nullable String remoteAddr) {
        fillRequest(request, protocol, method, remoteAddr);

        fillUrlRelatedFields(request, scheme, serverName, serverPort, requestURI, queryString);
    }

    protected void fillRequest(Request request, String protocol, String method, @Nullable String remoteAddr) {
        request.withHttpVersion(protocol);
        request.withMethod(method);

        request.getSocket()
            .withRemoteAddress(remoteAddr);
    }

    protected void fillUrlRelatedFields(Request request, @Nullable String scheme, @Nullable String serverName, int serverPort, String requestURI, @Nullable String queryString) {
        request.getUrl().fillFrom(scheme,
            serverName,
            serverPort,
            requestURI,
            queryString);
    }

    public boolean isCaptureHeaders() {
        return coreConfiguration.isCaptureHeaders();
    }
}
