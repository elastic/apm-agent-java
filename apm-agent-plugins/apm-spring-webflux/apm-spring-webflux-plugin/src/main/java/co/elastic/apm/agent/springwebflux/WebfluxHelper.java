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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import co.elastic.apm.agent.util.TransactionNameUtils;
import org.reactivestreams.Publisher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import javax.annotation.Nullable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_LOW_LEVEL_FRAMEWORK;
import static org.springframework.web.reactive.function.server.RouterFunctions.MATCHING_PATTERN_ATTRIBUTE;

public class WebfluxHelper {

    private static final Logger log = LoggerFactory.getLogger(WebfluxHelper.class);

    public static final String TRANSACTION_ATTRIBUTE = WebfluxHelper.class.getName() + ".transaction";
    private static final String SUBSCRIBER_ATTRIBUTE = WebfluxHelper.class.getName() + ".wrapped_subscriber";

    private static final String SERVLET_TRANSACTION = WebfluxHelper.class.getName() + ".servlet_transaction";
    public static final String SSE_EVENT_CLASS = "org.springframework.http.codec.ServerSentEvent";

    private static final HeaderGetter HEADER_GETTER = new HeaderGetter();

    private static final CoreConfiguration coreConfig;
    private static final WebConfiguration webConfig;

    private static final WeakMap<HandlerMethod, Boolean> ignoredHandlerMethods = WeakConcurrent.buildMap();

    static {
        coreConfig = GlobalTracer.requireTracerImpl().getConfig(CoreConfiguration.class);
        webConfig = GlobalTracer.requireTracerImpl().getConfig(WebConfiguration.class);
    }


    @Nullable
    public static Transaction getOrCreateTransaction(Tracer tracer, ServerWebExchange exchange) {

        Transaction transaction = WebfluxServletHelper.getServletTransaction(exchange);
        boolean fromServlet = transaction != null;

        if (!fromServlet) {
            transaction = tracer.startChildTransaction(exchange.getRequest().getHeaders(), HEADER_GETTER, ServerWebExchange.class.getClassLoader());
        }

        if (transaction == null) {
            return null;
        }

        transaction.withType("request").activate();

        // store transaction in exchange to make it easy to retrieve from other handlers
        exchange.getAttributes().put(TRANSACTION_ATTRIBUTE, transaction);

        exchange.getAttributes().put(SERVLET_TRANSACTION, fromServlet);

        return transaction;
    }

    public static boolean isServletTransaction(ServerWebExchange exchange) {
        return Boolean.TRUE == exchange.getAttributes().get(SERVLET_TRANSACTION);
    }

    public static <T> Mono<T> wrapDispatcher(Mono<T> mono, Transaction transaction, ServerWebExchange exchange) {
        return doWrap(mono, transaction, exchange, "webflux-dispatcher");
    }


    private static <T> Mono<T> doWrap(Mono<T> mono, final Transaction transaction, final ServerWebExchange exchange, final String description) {
        //noinspection Convert2Lambda,rawtypes,Convert2Diamond
        mono = mono.transform(Operators.liftPublisher(new BiFunction<Publisher, CoreSubscriber<? super T>, CoreSubscriber<? super T>>() {
            @Override // liftPublisher too (or whole transform param)
            public CoreSubscriber<? super T> apply(Publisher publisher, CoreSubscriber<? super T> subscriber) {
                log.trace("wrapping {} subscriber with transaction {}", description, transaction);

                // If there is already an active transaction, it's tempting to avoid wrapping as the context propagation
                // would be already provided through reactor instrumentation. However, we can't as the transaction
                // name would not be properly set to match Webflux annotated controllers/router definitions.
                TransactionAwareSubscriber<T> wrappedSubscriber = new TransactionAwareSubscriber<>(subscriber, transaction, exchange, description);

                // we should only have a single level of subscriber wrapping per exchange thus we can use it for storing
                // back-references used when request processing is cancelled
                if (null != exchange.getAttributes().put(SUBSCRIBER_ATTRIBUTE, wrappedSubscriber)) {
                    log.debug("more than one wrapping subscriber in exchange");
                }

                return wrappedSubscriber;
            }
        }));
        return mono;
    }

    public static void endTransaction(@Nullable Throwable thrown, @Nullable Transaction transaction, ServerWebExchange exchange) {
        if (transaction == null) {
            // already discarded
            return;
        }

        Object attribute = exchange.getAttributes().remove(WebfluxHelper.TRANSACTION_ATTRIBUTE);
        if (attribute != transaction) {
            // transaction might be already terminated due to instrumentation of more than one
            // dispatcher/handler/invocation-handler class
            return;
        }

        if (ignoreTransaction(exchange)) {
            transaction.ignoreTransaction();
            transaction.end();
            return;
        }

        int namePriority;
        String path;
        PathPattern pattern = exchange.getAttribute(MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            namePriority = PRIO_HIGH_LEVEL_FRAMEWORK;
            path = pattern.getPatternString();
        } else {
            namePriority = PRIO_LOW_LEVEL_FRAMEWORK + 1;
            if (webConfig.isUsePathAsName()) {
                path = exchange.getRequest().getPath().value();
            } else {
                path = "unknown route";
            }
        }

        TransactionNameUtils.setNameFromHttpRequestPath(
            exchange.getRequest().getMethodValue(),
            path,
            transaction.getAndOverrideName(namePriority, false),
            webConfig.getUrlGroups()
        );

        // Fill request/response details if they haven't been already by another HTTP plugin (servlet or other).
        if (!transaction.getContext().getRequest().hasContent()) {
            fillRequest(transaction, exchange);
            fillResponse(transaction, exchange);
        }

        transaction.captureException(thrown);

        // In case transaction has been created by Servlet, we should not terminate it as the Servlet instrumentation
        // will take care of this.
        if (!WebfluxHelper.isServletTransaction(exchange)) {
            transaction.end();
        }
    }


    private static boolean ignoreTransaction(ServerWebExchange exchange) {
        // Annotated controllers have the invoked handler method available in exchange
        // thus we can rely on this to ignore methods that return ServerSideEvents which should not report transactions
        Object attribute = exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(attribute instanceof HandlerMethod)) {
            return false;
        }

        HandlerMethod handlerMethod = (HandlerMethod) attribute;
        Boolean ignoredCache = ignoredHandlerMethods.get(handlerMethod);
        if (ignoredCache != null) {
            return ignoredCache;
        }

        Type returnType = handlerMethod.getMethod().getGenericReturnType();
        if (!(returnType instanceof ParameterizedType)) {
            ignoredHandlerMethods.put(handlerMethod, false);
            return false;
        }

        Type[] genReturnTypes = ((ParameterizedType) returnType).getActualTypeArguments();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < genReturnTypes.length; i++) {
            if (genReturnTypes[i].getTypeName().startsWith(WebfluxHelper.SSE_EVENT_CLASS)) {
                ignoredHandlerMethods.put(handlerMethod, true);
                return true;
            }
        }

        ignoredHandlerMethods.put(handlerMethod, false);
        return false;
    }

    private static void fillRequest(Transaction transaction, ServerWebExchange exchange) {
        ServerHttpRequest serverRequest = exchange.getRequest();
        Request request = transaction.getContext().getRequest();

        request.withMethod(serverRequest.getMethodValue());

        InetSocketAddress remoteAddress = serverRequest.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            request.getSocket()
                .withRemoteAddress(remoteAddress.getAddress().getHostAddress());
        }

        request.getUrl().fillFrom(serverRequest.getURI());

        if (coreConfig.isCaptureHeaders()) {
            copyHeaders(serverRequest.getHeaders(), request.getHeaders());
            copyCookies(serverRequest.getCookies(), request.getCookies());
        }

    }

    private static void fillResponse(Transaction transaction, ServerWebExchange exchange) {
        ServerHttpResponse serverResponse = exchange.getResponse();
        HttpStatus statusCode = serverResponse.getStatusCode();
        int status = statusCode != null ? statusCode.value() : 200;

        transaction.withResultIfUnset(ResultUtil.getResultByHttpStatus(status));

        Response response = transaction.getContext().getResponse();

        if (coreConfig.isCaptureHeaders()) {
            copyHeaders(serverResponse.getHeaders(), response.getHeaders());
        }

        response
            .withFinished(true)
            .withStatusCode(status);

    }

    private static void copyHeaders(HttpHeaders source, PotentiallyMultiValuedMap destination) {
        for (Map.Entry<String, List<String>> header : source.entrySet()) {
            for (String value : header.getValue()) {
                destination.add(header.getKey(), value);
            }
        }
    }

    private static void copyCookies(MultiValueMap<String, HttpCookie> source, PotentiallyMultiValuedMap destination) {
        for (Map.Entry<String, List<HttpCookie>> cookie : source.entrySet()) {
            for (HttpCookie value : cookie.getValue()) {
                destination.add(value.getName(), value.getValue());
            }
        }
    }

}
