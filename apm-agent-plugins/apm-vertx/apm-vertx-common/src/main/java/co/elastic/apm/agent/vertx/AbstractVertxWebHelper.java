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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.VersionUtils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractVertxWebHelper extends AbstractHttpTransactionHelper {

    private final Logger logger = LoggerFactory.getLogger(AbstractVertxWebHelper.class);
    public static final String CONTEXT_TRANSACTION_KEY = AbstractVertxWebHelper.class.getName() + ".transaction";
    public static final String FRAMEWORK_NAME = "Vert.x-Web";
    private static final String SPAN_TYPE = "request";

    private final MultiMapHeadersGetterSetter headerGetter = new MultiMapHeadersGetterSetter();

    protected AbstractVertxWebHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Nullable
    protected Transaction startOrGetTransaction(HttpServerRequest httpServerRequest) {
        Transaction transaction = null;
        if (tracer.currentTransaction() != null) {
            return tracer.currentTransaction();
        } else if (!isExcluded(httpServerRequest.uri(), null, httpServerRequest.headers().get(USER_AGENT_HEADER))) {
            transaction = tracer.startChildTransaction(httpServerRequest.headers(), headerGetter, httpServerRequest.getClass().getClassLoader());
        }

        return transaction;
    }

    @Nullable
    public abstract Transaction setRouteBasedNameForCurrentTransaction(RoutingContext routingContext);

    protected void setRouteBasedTransactionName(Transaction transaction, RoutingContext routingContext) {
        if (!webConfiguration.isUsePathAsName()) {
            StringBuilder transactionName = transaction.getAndOverrideName(AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK);
            if (transactionName != null) {
                transactionName.append(routingContext.request().method().name())
                    .append(" ").append(routingContext.currentRoute().getPath());
            }
        }
    }

    public void finalizeTransaction(HttpServerResponse httpServerResponse, Transaction transaction) {
        try {
            final Response response = transaction.getContext().getResponse();
            int status = httpServerResponse.getStatusCode();
            setResponseHeaders(transaction, httpServerResponse, response);

            fillResponse(response, null, status);
            transaction.withResultIfUnset(ResultUtil.getResultByHttpStatus(status));

            transaction.end();
        } catch (Throwable e) {
            logger.warn("Exception while capturing Elastic APM transaction", e);
        }
    }

    public void captureBody(@Nullable Transaction transaction, Buffer requestDataBuffer) {
        if (transaction == null || transaction.getContext().getRequest().getBodyBuffer() == null) {
            return;
        }

        Request request = transaction.getContext().getRequest();
        final CharBuffer bodyBuffer = request.getBodyBuffer();

        // duplicate only copies the buffer indexes without copying the actual data
        ByteBuf buffer = requestDataBuffer.getByteBuf().duplicate();

        final CoderResult coderResult = NettyByteTransfer.decodeUtf8BytesFromTransfer(buffer, bodyBuffer);
        if (coderResult.isError()) {
            request.setRawBody("[Non UTF-8 data]");
        } else if (coderResult.isOverflow()) {
            request.endOfBufferInput();
        }
    }

    protected void enrichRequest(HttpServerRequest httpServerRequest, Transaction transaction) {
        transaction.setFrameworkName(FRAMEWORK_NAME);
        transaction.setFrameworkVersion(VersionUtils.getVersion(HttpServerRequest.class, "io.vertx", "vertx-web"));
        transaction.withType(SPAN_TYPE);
        Request request = transaction.getContext().getRequest();
        String host = httpServerRequest.host();
        int idx = host.lastIndexOf(':');
        if (idx > 0) {
            host = host.substring(0, idx);
        }
        String method = httpServerRequest.method().name();
        String contentType = httpServerRequest.headers().get(CONTENT_TYPE_HEADER);

        fillRequest(request, httpServerRequest.version().toString(), method,
            httpServerRequest.scheme(), host,
            httpServerRequest.connection().localAddress().port(), httpServerRequest.path(), httpServerRequest.query(), httpServerRequest.remoteAddress().toString());

        applyDefaultTransactionName(httpServerRequest.method().name(), httpServerRequest.path(), null, transaction, 10);

        startCaptureBody(transaction, method, contentType);
        setRequestHeaders(transaction, httpServerRequest);
        setRequestParameters(transaction, httpServerRequest, method, contentType);
    }


    private void setResponseHeaders(Transaction transaction, HttpServerResponse httpServerResponse, Response response) {
        if (transaction.isSampled() && isCaptureHeaders()) {
            final Set<String> headerNames = httpServerResponse.headers().names();
            if (headerNames != null) {
                for (String headerName : headerNames) {
                    response.addHeader(headerName, httpServerResponse.headers().getAll(headerName));
                }
            }
        }
    }


    private void setRequestParameters(Transaction transaction, HttpServerRequest httpServerRequest, String method, String contentType) {
        if (transaction.isSampled() && captureParameters(method, contentType)) {
            final Map<String, String[]> parameterMap = new HashMap<>();
            for (String name : httpServerRequest.params().names()) {
                List<String> allValues = httpServerRequest.params().getAll(name);
                String[] paramValues = new String[allValues.size()];
                allValues.toArray(paramValues);
                parameterMap.put(name, paramValues);
            }
            fillRequestParameters(transaction, method, parameterMap, contentType);
        }
    }

    private void setRequestHeaders(Transaction transaction, HttpServerRequest httpServerRequest) {
        final Request req = transaction.getContext().getRequest();
        if (transaction.isSampled() && isCaptureHeaders()) {
            setCookies(httpServerRequest, req);

            final Set<String> headerNames = httpServerRequest.headers().names();
            if (headerNames != null) {
                for (String headerName : headerNames) {
                    Enumeration<String> headers = Collections.enumeration(httpServerRequest.headers().getAll(headerName));
                    req.addHeader(headerName, headers);
                }
            }
        }
    }

    private void setCookies(HttpServerRequest httpServerRequest, Request request) {
        String cookieString = httpServerRequest.headers().get(HttpHeaders.COOKIE);
        if (cookieString != null) {
            for (Cookie cookie : ServerCookieDecoder.LAX.decode(cookieString)) {
                request.addCookie(cookie.name(), cookie.value());
            }
        }
    }
}
