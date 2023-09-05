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
package co.elastic.apm.agent.micronaut;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.tracer.dispatch.AbstractHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.metadata.Request;
import co.elastic.apm.agent.tracer.metadata.Response;
import co.elastic.apm.agent.tracer.util.ResultUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpsExchange;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

public class RequestLifecycleAdvice {
    private static final Logger log = LoggerFactory.getLogger(RequestLifecycleAdvice.class);

    private static class ScopeWrapper {
        private final PropagatedContext.Scope propagatedScope;
        private final Transaction<?> transaction;

        public ScopeWrapper(PropagatedContext.Scope propagatedScope, Transaction<?> transaction) {
            this.propagatedScope = propagatedScope;
            this.transaction = transaction;
        }

        public PropagatedContext.Scope getPropagatedScope() {
            return propagatedScope;
        }

        public Transaction<?> getTransaction() {
            return transaction;
        }
    }

    public static class HeaderGetter extends AbstractHeaderGetter<String, HttpHeaders> implements TextHeaderGetter<HttpHeaders> {

        @Nullable
        @Override
        public String getFirstHeader(String headerName, HttpHeaders carrier) {
            return carrier.get(headerName);
        }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(
            @Advice.FieldValue("request") @Nullable Object requestUntyped
        ) {
        if(requestUntyped == null) {
            return null;
        }

        HttpRequest<?> typedRequest = (HttpRequest<?>) requestUntyped;

        Transaction<?> trx = GlobalTracer.get().startChildTransaction(typedRequest.getHeaders(), new HeaderGetter(), Thread.currentThread().getContextClassLoader());

        if(trx == null) {
            return null;
        }

        trx.setFrameworkName("micronaut");

        StringBuilder nameBuilder = trx.getAndOverrideName(AbstractSpan.PRIORITY_LOW_LEVEL_FRAMEWORK);

        if(nameBuilder == null) {
            trx.end();
            return null;
        }

        nameBuilder.append(typedRequest.getMethodName());
        nameBuilder.append(" ");
        nameBuilder.append(typedRequest.getUri().getPath());

        Request request = trx.getContext().getRequest();

        request.getSocket()
            .withRemoteAddress(typedRequest.getRemoteAddress().getAddress().getHostAddress());

        request.withHttpVersion(typedRequest.getHttpVersion().name())
            .withMethod(typedRequest.getMethodName());

        request.getUrl()
            .withProtocol("http")
            .withHostname(typedRequest.getRemoteAddress().getAddress().getHostAddress())
            .withPort(typedRequest.getServerAddress().getPort())
            .withPathname(typedRequest.getUri().getPath())
            .withSearch(typedRequest.getUri().getQuery());

        PropagatedContextElement elasticContextElement = new PropagatedContextElement(trx);

        PropagatedContext ctx = PropagatedContext.getOrEmpty().plus(elasticContextElement);

        return new ScopeWrapper(ctx.propagate(), trx);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable Object scopeUntyped,
        @Advice.Return @Nullable ExecutionFlow<MutableHttpResponse<?>> returnFlow,
        @Advice.Thrown @Nullable Throwable t) {

        if(scopeUntyped == null) {
            return;
        }

        ScopeWrapper wrapper = (ScopeWrapper) scopeUntyped;

        PropagatedContext.Scope scope = wrapper.propagatedScope;

        scope.close();

        Transaction<?> trx = wrapper.getTransaction();

        if(t != null) {
            finishTransaction(trx, null, t);
            return;
        }

        if (returnFlow == null) {
            return;
        }

        returnFlow.onComplete((res, ex) -> finishTransaction(trx, res, ex));
    }

    private static void finishTransaction(
        Transaction<?> trx,
        @Nullable MutableHttpResponse<?> response,
        @Nullable Throwable exception)
    {
        if(response != null) {
            trx
                .withResultIfUnset(ResultUtil.getResultByHttpStatus(response.code()))
                .captureException(exception);

            Response trxResponse = trx.getContext().getResponse();

            trxResponse
                .withFinished(true)
                .withStatusCode(response.code());
        }

        trx.end();
    }
}
