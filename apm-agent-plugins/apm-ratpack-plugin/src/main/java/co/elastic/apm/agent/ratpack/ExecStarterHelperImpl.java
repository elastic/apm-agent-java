/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.ratpack;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.web.ClientIpUtils;
import co.elastic.apm.agent.web.WebConfiguration;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.cookie.Cookie;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.exec.Execution;
import ratpack.func.Action;
import ratpack.handling.Context;
import ratpack.http.Headers;
import ratpack.http.HttpMethod;
import ratpack.http.MediaType;
import ratpack.http.Request;
import ratpack.registry.RegistrySpec;
import ratpack.server.ServerConfig;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class ExecStarterHelperImpl implements ExecStarterInstrumentation.ExecStarterHelper<Action<? super RegistrySpec>, TransactionHolder, Action<? super Execution>> {

    private static final Logger logger = LoggerFactory.getLogger(ExecStarterHelperImpl.class);

    @Override
    public Action<? super RegistrySpec> registerTransaction(final Action<? super RegistrySpec> action, final Class<TransactionHolder> transactionToken) {
        return action.append(new RegisterTransactionAction(transactionToken));
    }

    @SuppressWarnings("WeakerAccess")
    public static class RegisterTransactionAction implements Action<RegistrySpec> {
        private final Class<TransactionHolder> transactionToken;

        public RegisterTransactionAction(final Class<TransactionHolder> transactionToken) {

            this.transactionToken = transactionToken;
        }

        @Override
        public void execute(RegistrySpec registrySpec) {
            registrySpec.add(transactionToken, new TransactionHolderImpl());
        }
    }

    @Override
    public Action<? super Execution> startTransaction(final Action<? super Execution> action, final ElasticApmTracer tracer, final Class<TransactionHolder> transactionToken, final ClassLoader classLoader) {
        return action.prepend(new StartTransactionAction(tracer, transactionToken, classLoader));
    }

    @IgnoreJRERequirement
    @VisibleForAdvice
    public static class StartTransactionAction implements Action<Execution> {

        private final ElasticApmTracer tracer;
        private final Class<TransactionHolder> transactionToken;
        private final ClassLoader classLoader;
        private final CoreConfiguration coreConfiguration;
        private final WebConfiguration webConfiguration;

        @SuppressWarnings("WeakerAccess")
        @VisibleForAdvice
        public StartTransactionAction(final ElasticApmTracer tracer, final Class<TransactionHolder> transactionToken, final ClassLoader classLoader) {
            this.tracer = tracer;
            this.coreConfiguration = tracer.getConfig(CoreConfiguration.class);
            this.webConfiguration = tracer.getConfig(WebConfiguration.class);
            this.transactionToken = transactionToken;
            this.classLoader = classLoader;
        }

        @Override
        public void execute(final Execution execution) {

            final Optional<Request> optionalRequest = execution.maybeGet(Request.class);

            if (!optionalRequest.isPresent()) {
                return;
            }

            final Optional<TransactionHolder> optional = execution.maybeGet(transactionToken);

            if (!optional.isPresent()) {
                return;
            }

            final Request request = optionalRequest.get();
            final Headers headers = request.getHeaders();

            //noinspection ConstantConditions
            if (coreConfiguration != null
                && coreConfiguration.isActive()
                && !isExcluded("/" + request.getPath(), headers.get(HttpHeaderNames.USER_AGENT))
            ) {

                final TransactionHolder transactionHolder = optional.get();

                Transaction transaction = transactionHolder.getTransaction();
                final Transaction currentTransaction = tracer.currentTransaction();

                if (currentTransaction == null || transaction != null) {

                    transaction = tracer.startTransaction(
                        TraceContext.fromTraceparentHeader(),
                        headers.get(TraceContext.TRACE_PARENT_HEADER),
                        classLoader);

                    logger.debug("Starting transaction [{}] on execution [{}].", transaction, execution);

                    execution.maybeGet(Context.TYPE)
                        .ifPresent(new FillTransactionRequest(transaction, webConfiguration));

                    transactionHolder.setTransaction(transaction);

                    transaction.activate();

                } else {

                    //noinspection ConstantConditions
                    logger.warn("Not starting transaction. Current transaction [{}] and stored transaction [{}] did not pass assertions.", currentTransaction, transaction);
                }
            }
        }

        // @see co.elastic.apm.agent.servlet.ServletTransactionHelper.isExcluded
        private boolean isExcluded(String path, @Nullable String userAgentHeader) {

            final WildcardMatcher excludeUrlMatcher = WildcardMatcher.anyMatch(webConfiguration.getIgnoreUrls(), path);
            if (excludeUrlMatcher != null) {
                logger.debug("Not tracing this request as the URL {} is ignored by the matcher {}",
                    path, excludeUrlMatcher);
            }
            final WildcardMatcher excludeAgentMatcher = userAgentHeader != null ? WildcardMatcher.anyMatch(webConfiguration.getIgnoreUserAgents(), userAgentHeader) : null;
            if (excludeAgentMatcher != null) {
                logger.debug("Not tracing this request as the User-Agent {} is ignored by the matcher {}",
                    userAgentHeader, excludeAgentMatcher);
            }
            return excludeUrlMatcher != null || excludeAgentMatcher != null;
        }
    }

    @Override
    public Action<? super Execution> endTransaction(final Action<? super Execution> action, final ElasticApmTracer tracer, final Class<TransactionHolder> transactionToken) {
        return action.append(new EndTransactionAction(transactionToken, tracer));
    }

    @SuppressWarnings("WeakerAccess")
    public static class EndTransactionAction implements Action<Execution> {
        private final Class<TransactionHolder> transactionToken;
        private final ElasticApmTracer tracer;

        public EndTransactionAction(final Class<TransactionHolder> transactionToken, final ElasticApmTracer tracer) {
            this.transactionToken = transactionToken;
            this.tracer = tracer;
        }

        @Override
        @IgnoreJRERequirement
        public void execute(final Execution execution) {

            final Optional<TransactionHolder> optional = execution.maybeGet(transactionToken);

            //noinspection OptionalIsPresent
            if (optional.isPresent()) {

                final TransactionHolder transactionHolder = optional.get();

                final Transaction currentTransaction = tracer.currentTransaction();
                final Transaction transaction = transactionHolder.getTransaction();

                if (transaction != null && transaction.equals(currentTransaction)) {

                    if (transaction.equals(currentTransaction)) {

                        logger.debug("Ending transaction [{}] on execution [{}].", transaction, execution);

                        final Optional<Context> context = execution.maybeGet(Context.TYPE);

                        context.ifPresent(new FillTransactionName(transaction));
                        context.ifPresent(new FillTransactionResponse(transaction, tracer.getConfig(WebConfiguration.class)));

                        // clear the transaction storage. Need to clear tracking before deactivation.
                        transactionHolder.setTransaction(null);

                        transaction.deactivate();
                        transaction.end();
                    } else {

                        logger.warn("Not ending transaction. Current transaction [{}] and stored transaction [{}] did not pass assertions. ", currentTransaction, transaction);
                    }
                }
            }
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class FillTransactionRequest implements Consumer<Context> {

        private final Transaction transaction;

        @Nullable
        private final WebConfiguration webConfiguration;

        public FillTransactionRequest(final Transaction transaction, @Nullable final WebConfiguration webConfiguration) {
            this.transaction = transaction;
            this.webConfiguration = webConfiguration;
        }

        @Override
        public void accept(final Context context) {

            fillRequestContext(transaction.getContext().getRequest(), context);
        }

        private void fillRequestContext(final co.elastic.apm.agent.impl.context.Request request, final Context context) {

            if (transaction.isSampled() && webConfiguration != null && webConfiguration.isCaptureHeaders()) {

                for (final Cookie cookie : context.getRequest().getCookies()) {
                    request.addCookie(cookie.name(), cookie.value());
                }

                for (final String name : context.getRequest().getHeaders().getNames()) {
                    for (final String value : context.getRequest().getHeaders().getAll(name)) {
                        request.addHeader(name, value);
                    }
                }
            }

            final ServerConfig serverConfig = context.getServerConfig();

            request.withHttpVersion(getHttpVersion(context.getRequest().getProtocol()));
            request.withMethod(context.getRequest().getMethod().getName());

            request.getSocket()
                .withRemoteAddress(ClientIpUtils.getRealIp(request.getHeaders(), context.getRequest().getRemoteAddress().toString()));

            request.getUrl()
                .withPort(serverConfig.getPort())
                .withPathname(context.getRequest().getUri())
                .withSearch(context.getRequest().getQuery());

            final MediaType contentType = context.getRequest().getContentType();
            final HttpMethod httpMethod = context.getRequest().getMethod();

            if (transaction.isSampled()
                && webConfiguration != null
                && webConfiguration.getCaptureBody() != WebConfiguration.EventType.OFF
                && hasBody(context.getRequest())
            ) {

                for (String key : context.getRequest().getQueryParams().keySet()) {
                    for (String value : context.getRequest().getQueryParams().getAll(key)) {
                        request.addFormUrlEncodedParameter(key, value);
                    }
                }
            }
        }

        boolean hasBody(final Request request) {
            return TransactionHolderImpl.METHODS_WITH_BODY.contains(request.getMethod())
                && !request.getContentType().isEmpty()
                && request.getContentType().isForm();
        }


        private String getHttpVersion(final String protocol) {
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

    @SuppressWarnings("WeakerAccess")
    public static class FillTransactionName implements Consumer<Context> {

        private final Transaction transaction;

        public FillTransactionName(final Transaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public void accept(final Context context) {

            fillTransactionName(transaction, context);
        }

        private void fillTransactionName(final Transaction transaction, final Context context) {

            transaction.appendToName(context.getRequest().getMethod().getName());
            transaction.appendToName(" /");
            transaction.appendToName(context.getPathBinding().getDescription());
        }
    }

    public static class FillTransactionResponse implements Consumer<Context> {

        private final Transaction transaction;

        @Nullable
        private final WebConfiguration webConfiguration;

        @SuppressWarnings("WeakerAccess")
        public FillTransactionResponse(final Transaction transaction, @Nullable final WebConfiguration webConfiguration) {
            this.transaction = transaction;
            this.webConfiguration = webConfiguration;
        }

        @Override
        public void accept(final Context context) {

            transaction.withResult(context.getResponse().getStatus().toString());

            fillResponseContext(transaction.getContext().getResponse(), context);
        }

        private void fillResponseContext(final Response response, final Context context) {
            if (transaction.isSampled() && webConfiguration != null && webConfiguration.isCaptureHeaders()) {
                for (final String name : context.getResponse().getHeaders().getNames()) {
                    response.addHeader(name, context.getResponse().getHeaders().getAll(name));
                }
            }
            response.withStatusCode(context.getResponse().getStatus().getCode());
            response.withFinished(true);
        }
    }
}
