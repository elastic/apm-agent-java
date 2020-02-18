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
import co.elastic.apm.agent.impl.transaction.Span;
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
 * Since instances of this class are loaded through {@see co.elastic.apm.agent.bci.HelperClassManager}, we can use all
 * classes that are part of the gRPC API.
 */
@SuppressWarnings("unused")
public class GrpcHelperImpl implements GrpcHelper {

    /**
     * Map of all in-flight spans, is only used by client part.
     * Key is {@link ClientCall}, but referred as {@link Object} to avoid loading any gRPC reference in the bootstrap classloader
     */
    private static final WeakConcurrentMap<ClientCall<?,?>, Span> inFlightSpans;
    /**
     * Map of all in-flight {@link ClientCall instances} with the {@link ClientCall.Listener} instance that have been used to
     * start them as key.
     */
    private static final WeakConcurrentMap<ClientCall.Listener<?>, ClientCall<?,?>> inFlightListeners;

    private static final TextHeaderSetter<Metadata> headerSetter;

    static {
        inFlightListeners = new WeakConcurrentMap.WithInlinedExpunction<ClientCall.Listener<?>, ClientCall<?, ?>>();
        inFlightSpans = new WeakConcurrentMap.WithInlinedExpunction<ClientCall<?, ?>, Span>();
        headerSetter = new GrpcHeaderSetter();
    }

    // transaction management (server part)

    @Override
    public void startTransaction(ElasticApmTracer tracer, ClassLoader cl, ServerCall<?, ?> serverCall, Metadata headers) {

        String methodName = serverCall.getMethodDescriptor().getFullMethodName();

        tracer.startChildTransaction(headers, GrpcHeaderGetter.getInstance(), cl)
            .withName(methodName)
            .withType("request")
            .activate();
    }

    @Override
    public void endTransaction(Status status, @Nullable Throwable thrown, @Nullable Transaction transaction) {
        if (transaction == null || transaction.getResult() != null) {
            return;
        }

        // transaction might be terminated early in case of thrown exception
        // from method signature it's a runtime exception, thus very likely an issue in server implementation
        transaction
            .withResult(status.getCode().name())
            .captureException(thrown)
            .deactivate()
            .end();
    }

    // exit span management (client part)

    @Nullable
    @Override
    public Span createExitSpanAndActivate(@Nullable Transaction transaction, @Nullable MethodDescriptor<?, ?> method) {
        Span span;
        if (null == transaction) {
            return null;
        }

        span = transaction.createExitSpan();
        if (span == null) {
            // as it's an external call, we only need a single span for nested calls
            return null;
        }

        return span.withName(method == null ? null : method.getFullMethodName())
            .withType("external")
            .withSubtype(GRPC)
            .activate();
    }

    @Override
    public void registerSpanAndDeactivate(@Nullable Span span, ClientCall<?, ?> clientCall) {
        if (span != null) {
            inFlightSpans.put(clientCall, span);
            span.deactivate();
        }
    }

    @Override
    public void startSpan(ClientCall<?, ?> clientCall, ClientCall.Listener<?> responseListener, Metadata headers) {
        // span should already have been registered
        Span span = inFlightSpans.get(clientCall);
        if (span == null) {
            return;
        }

        inFlightListeners.put(responseListener, clientCall);
        span.setStartTimestampNow();
        span.getTraceContext().setOutgoingTraceContextHeaders(headers, headerSetter);
    }

    @Override
    public void endSpan(ClientCall.Listener<?> responseListener, @Nullable Throwable thrown) {
        ClientCall<?, ?> clientCall = inFlightListeners.get(responseListener);
        Span span = null;
        if (clientCall != null) {
            span = inFlightSpans.get(clientCall);
        }
        if (span == null) {
            return;
        }

        span.captureException(thrown)
            .end();

        inFlightListeners.remove(responseListener);
        inFlightSpans.remove(clientCall);
    }

    @Override
    public void captureListenerException(ClientCall.Listener<?> responseListener, @Nullable Throwable thrown) {
        if (thrown != null) {
            Span span = getSpanFromListener(responseListener);
            if (span != null) {
                span.captureException(thrown);
            }
        }
    }

    @Override
    public void enrichSpanContext(ClientCall<?, ?> clientCall, @Nullable String authority) {
        if (authority == null) {
            return;
        }

        Span span = inFlightSpans.get(clientCall);
        if (span == null) {
            return;
        }

        Destination destination = span.getContext().getDestination()
            .withAddressPort(authority);

        destination.getService()
            .withName(GRPC)
            .withResource(authority)
            .withType(GRPC);
    }

    @Nullable
    private Span getSpanFromListener(ClientCall.Listener<?> responseListener) {
        ClientCall<?, ?> clientCall = inFlightListeners.get(responseListener);
        Span span = null;
        if (clientCall != null) {
            span = inFlightSpans.get(clientCall);
        }
        return span;
    }

}
