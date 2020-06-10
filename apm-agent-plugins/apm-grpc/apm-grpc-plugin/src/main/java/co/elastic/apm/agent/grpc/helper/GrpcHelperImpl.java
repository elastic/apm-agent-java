/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.grpc.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.AbstractHeaderGetter;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;

import javax.annotation.Nullable;

/**
 * Helper class for gRPC client and server calls.
 *
 * <br>
 * Since instances of this class are loaded through {@link co.elastic.apm.agent.bci.HelperClassManager}, we can use all
 * classes that are part of the gRPC API.
 */
@SuppressWarnings("unused")
public class GrpcHelperImpl implements GrpcHelper {

    private static final WeakConcurrentMap<ClientCall<?, ?>, Span> clientCallSpans;
    private static final WeakConcurrentMap<ClientCall.Listener<?>, Span> clientCallListenerSpans;

    /**
     * Map of all in-flight {@link Transaction} with {@link ServerCall.Listener} instance as key.
     */
    private static final WeakConcurrentMap<ServerCall.Listener<?>, Transaction> serverListenerTransactions;

    /**
     * Map of all in-flight {@link Transaction} with {@link ServerCall} instance as key.
     */
    private static final WeakConcurrentMap<ServerCall<?, ?>, Transaction> serverCallTransactions;


    /**
     * gRPC header cache used to minimize allocations
     */
    private static final WeakConcurrentMap.WithInlinedExpunction<String, Metadata.Key<String>> headerCache;

    private static final TextHeaderSetter<Metadata> headerSetter;
    private static final TextHeaderGetter<Metadata> headerGetter;

    static {
        clientCallSpans = new WeakConcurrentMap.WithInlinedExpunction<ClientCall<?, ?>, Span>();
        clientCallListenerSpans = new WeakConcurrentMap.WithInlinedExpunction<ClientCall.Listener<?>, Span>();

        serverListenerTransactions = new WeakConcurrentMap.WithInlinedExpunction<ServerCall.Listener<?>, Transaction>();
        serverCallTransactions = new WeakConcurrentMap.WithInlinedExpunction<ServerCall<?, ?>, Transaction>();

        headerCache = new WeakConcurrentMap.WithInlinedExpunction<String, Metadata.Key<String>>();

        headerSetter = new GrpcHeaderSetter();
        headerGetter = new GrpcHeaderGetter();
    }

    // transaction management (server part)

    @Override
    public void startAndRegisterTransaction(ElasticApmTracer tracer, ClassLoader cl, ServerCall<?, ?> serverCall, Metadata headers, ServerCall.Listener<?> listener) {
        MethodDescriptor<?, ?> methodDescriptor = serverCall.getMethodDescriptor();

        // ignore non-unary method calls for now
        if (methodDescriptor.getType() != MethodDescriptor.MethodType.UNARY) {
            return;
        }

        Transaction transaction = tracer.startChildTransaction(headers, headerGetter, cl);
        if (transaction == null) {
            return;
        }

        transaction.withName(methodDescriptor.getFullMethodName())
            .withType("request");

        serverCallTransactions.put(serverCall, transaction);
        serverListenerTransactions.put(listener, transaction);

    }

    @Override
    public void setTransactionStatus(Status status, @Nullable Throwable thrown, ServerCall<?, ?> serverCall) {
        Transaction transaction = serverCallTransactions.remove(serverCall);

        if (transaction != null) {
            setTransactionStatus(status, thrown, transaction);
        }
    }

    private void setTransactionStatus(Status status, @Nullable Throwable thrown, Transaction transaction) {
        transaction
            .withResultIfUnset(status.getCode().name())
            .captureException(thrown);
    }

    @Nullable
    @Override
    public Transaction enterServerListenerMethod(ServerCall.Listener<?> listener) {
        Transaction transaction = serverListenerTransactions.get(listener);
        if (transaction != null) {
            transaction.activate();
        }
        return transaction;
    }

    @Override
    public void exitServerListenerMethod(@Nullable Throwable thrown,
                                         ServerCall.Listener<?> listener,
                                         @Nullable Transaction transaction,
                                         boolean isLastMethod) {
        if (transaction == null) {
            return;
        }

        transaction.deactivate();

        if (null != thrown) {
            // when there is a runtime exception thrown in one of the listener methods the calling code will catch it
            // and set 'unknown' status, we just replicate this behavior as we don't instrument the part that does this
            setTransactionStatus(Status.UNKNOWN, thrown, transaction);
        } else if (isLastMethod) {
            // transaction status will be set by ServerCall.close instrumentation
            transaction.end();
            serverListenerTransactions.remove(listener);
        }

    }

    // exit span management (client part)

    @Override
    @Nullable
    public Span startSpan(@Nullable AbstractSpan<?> parent, @Nullable MethodDescriptor<?, ?> method, @Nullable String authority) {
        if (null == parent) {
            return null;
        }

        // we only support unary method calls and ignore others for now
        if (method != null && method.getType() != MethodDescriptor.MethodType.UNARY) {
            return null;
        }

        Span span = parent.createExitSpan();
        if (span == null) {
            // as it's an external call, we only need a single span for nested calls
            return null;
        }

        span.withName(method == null ? null : method.getFullMethodName())
            .withType("external")
            .withSubtype(GRPC);

        if (authority != null) {
            Destination destination = span.getContext().getDestination()
                .withAddressPort(authority);

            destination.getService()
                .withName(GRPC)
                .withResource(authority)
                .withType(GRPC);
        }
        return span.activate();
    }

    @Override
    public void registerSpan(@Nullable ClientCall<?, ?> clientCall, Span span) {
        if (clientCall != null) {
            clientCallSpans.put(clientCall, span);
        }
        span.deactivate();
    }

    @Override
    public void clientCallStart(ClientCall<?, ?> clientCall, ClientCall.Listener<?> listener, Metadata headers) {
        // span should already have been registered
        // no other lookup by client call is required, thus removing entry
        Span span = clientCallSpans.remove(clientCall);
        if (span == null) {
            return;
        }

        clientCallListenerSpans.put(listener, span);

        span.propagateTraceContext(headers, headerSetter);
    }

    @Override
    public void captureListenerException(ClientCall.Listener<?> listener, @Nullable Throwable thrown) {
        if (thrown != null) {
            Span span = clientCallListenerSpans.get(listener);
            if (span != null) {
                span.captureException(thrown);
            }
        }
    }

    @Override
    @Nullable
    public Span enterClientListenerMethod(ClientCall.Listener<?> listener) {
        Span span = clientCallListenerSpans.get(listener);
        if (span != null) {
            span.activate();
        }
        return span;
    }

    @Override
    public void exitClientListenerMethod(@Nullable Throwable thrown,
                                         ClientCall.Listener<?> listener,
                                         @Nullable Span span,
                                         boolean isLastMethod) {

        if (span != null) {
            span.captureException(thrown)
                .deactivate();
        }

        if (isLastMethod) {
            clientCallListenerSpans.remove(listener);
            if (span != null) {
                span.end();
            }
        }
    }

    public static class GrpcHeaderSetter implements TextHeaderSetter<Metadata> {

        @Override
        public void setHeader(String headerName, String headerValue, Metadata carrier) {
            carrier.put(getHeader(headerName), headerValue);
        }

    }

    public static class GrpcHeaderGetter extends AbstractHeaderGetter<String, Metadata> implements TextHeaderGetter<Metadata> {

        @Nullable
        @Override
        public String getFirstHeader(String headerName, Metadata carrier) {
            return carrier.get(getHeader(headerName));
        }

    }

    private static Metadata.Key<String> getHeader(String headerName) {
        Metadata.Key<String> key = headerCache.get(headerName);
        if (key == null) {
            key = Metadata.Key.of(headerName, Metadata.ASCII_STRING_MARSHALLER);
            headerCache.put(headerName, key);
        }
        return key;
    }
}
