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
package co.elastic.apm.agent.httpserver;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.tracer.metadata.Request;
import co.elastic.apm.agent.tracer.metadata.Response;
import co.elastic.apm.agent.util.TransactionNameUtils;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HttpHandlerAdvice {

    private static final Tracer tracer;
    private static final HttpServerHelper serverHelper;
    private static final WebConfiguration webConfiguration;
    private static final CoreConfiguration coreConfiguration;

    static {
        tracer = GlobalTracer.get();
        serverHelper = new HttpServerHelper(tracer.getConfig(WebConfiguration.class));
        webConfiguration = tracer.getConfig(WebConfiguration.class);
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterHandle(@Advice.Argument(0) HttpExchange exchange) {

        if (tracer.currentTransaction() != null
            || serverHelper.isRequestExcluded(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("User-Agent"))) {
            return null;
        }

        Transaction<?> transaction = tracer.startChildTransaction(exchange.getRequestHeaders(), HeadersHeaderGetter.INSTANCE, Thread.currentThread().getContextClassLoader());
        if (transaction == null) {
            return null;
        }

        TransactionNameUtils.setNameFromHttpRequestPath(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getPath(),
            transaction.getAndOverrideName(AbstractSpan.PRIORITY_LOW_LEVEL_FRAMEWORK),
            webConfiguration.getUrlGroups());

        transaction.withType(Transaction.TYPE_REQUEST)
            .setFrameworkName("JDK HTTP Server");

        Request request = transaction.getContext().getRequest();

        request.getSocket()
            .withRemoteAddress(exchange.getRemoteAddress().getAddress().getHostAddress());

        request.withHttpVersion(exchange.getProtocol())
            .withMethod(exchange.getRequestMethod());

        request.getUrl()
            .withProtocol(exchange instanceof HttpsExchange ? "https" : "http")
            .withHostname(getHostname(exchange))
            .withPort(exchange.getLocalAddress().getPort())
            .withPathname(exchange.getRequestURI().getPath())
            .withSearch(exchange.getRequestURI().getQuery());

        if (transaction.isSampled() && coreConfiguration.isCaptureHeaders()) {
            Headers headers = exchange.getRequestHeaders();
            if (headers != null) {
                for (Map.Entry<String, List<String>> header : headers.entrySet()) {
                    if ("Cookie".equalsIgnoreCase(header.getKey())) {
                        for (String[] cookie : CookieHelper.getCookies(header.getValue())) {
                            request.addCookie(cookie[0], cookie[1]);
                        }
                    } else {
                        request.addHeader(header.getKey(), Collections.enumeration(header.getValue()));
                    }
                }
            }
        }

        return transaction.activate();
    }

    private static String getHostname(HttpExchange exchange) {
        List<String> hostHeader = exchange.getRequestHeaders().get("Host");
        if (hostHeader != null) {
            String hostname = hostHeader.get(0);

            int idx = hostname.lastIndexOf(':');
            if (idx > 0) {
                String port = String.valueOf(exchange.getLocalAddress().getPort());
                if (idx + 1 + port.length() == hostname.length() && hostname.endsWith(port)) {
                    hostname = hostname.substring(0, idx); // remove port from hostname
                }
            }

            return hostname;
        }
        return exchange.getLocalAddress().getAddress().getHostName();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitHandle(@Advice.Argument(0) HttpExchange exchange, @Advice.Enter @Nullable Object transactionOrNull, @Advice.Thrown @Nullable Throwable t) {
        if (transactionOrNull == null) {
            return;
        }

        Transaction<?> transaction = (Transaction<?>) transactionOrNull;
        transaction
            .withResultIfUnset(ResultUtil.getResultByHttpStatus(exchange.getResponseCode()))
            .captureException(t);

        Response response = transaction.getContext().getResponse();
        response
            .withFinished(true)
            .withStatusCode(exchange.getResponseCode());

        if (transaction.isSampled() && coreConfiguration.isCaptureHeaders()) {
            Headers headers = exchange.getResponseHeaders();
            if (headers != null) {
                for (Map.Entry<String, List<String>> header : headers.entrySet()) {
                    response.addHeader(header.getKey(), header.getValue());
                }
            }
        }

        transaction.deactivate().end();
    }
}
