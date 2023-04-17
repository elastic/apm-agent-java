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
package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.collections.WeakConcurrentProviderImpl;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.dispatch.AbstractHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.HeaderUtils;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;

import javax.annotation.Nullable;

/**
 * Helper class for gRPC client and server calls.
 */
public class GrpcHelper {

    static String GRPC = "grpc";

    private static final String FRAMEWORK_NAME = "gRPC";

    private static final GrpcHelper INSTANCE = new GrpcHelper();

    public static GrpcHelper getInstance() {
        return INSTANCE;
    }

    /**
     * Map of all in-flight {@link Span} with {@link ClientCall} instance as key.
     */
    private final WeakMap<ClientCall<?, ?>, Span<?>> clientCallSpans;

    /**
     * Map of all in-flight {@link Span} with {@link ClientCall} instance as key.
     */
    private final WeakMap<ClientCall<?, ?>, Span<?>> delayedClientCallSpans;

    /**
     * Map of all in-flight {@link Span} with {@link ClientCall.Listener} instance as key.
     */
    private final WeakMap<ClientCall.Listener<?>, Span<?>> clientCallListenerSpans;

    /**
     * Map of all in-flight {@link Transaction} with {@link ServerCall.Listener} instance as key.
     */
    private final WeakMap<ServerCall.Listener<?>, Transaction<?>> serverListenerTransactions;

    /**
     * Map of all in-flight {@link Transaction} with {@link ServerCall} instance as key.
     */
    private final WeakMap<ServerCall<?, ?>, Transaction<?>> serverCallTransactions;

    /**
     * gRPC header cache used to minimize allocations
     */
    private final WeakMap<String, Metadata.Key<String>> headerCache;

    private final TextHeaderSetter<Metadata> headerSetter;
    private final TextHeaderGetter<Metadata> headerGetter;

    private final Tracer tracer;

    public GrpcHelper() {
        clientCallSpans = WeakConcurrentProviderImpl.createWeakSpanMap();
        delayedClientCallSpans = WeakConcurrentProviderImpl.createWeakSpanMap();
        clientCallListenerSpans = WeakConcurrentProviderImpl.createWeakSpanMap();

        serverListenerTransactions = WeakConcurrentProviderImpl.createWeakSpanMap();
        serverCallTransactions = WeakConcurrentProviderImpl.createWeakSpanMap();

        headerCache = WeakConcurrent.buildMap();

        headerSetter = new GrpcHeaderSetter();
        headerGetter = new GrpcHeaderGetter();

        tracer = GlobalTracer.get();
    }

    // transaction management (server part)

    /**
     * Starts transaction, when not {@literal null}, transaction is activated.
     *
     * @param tracer     tracer
     * @param cl         classloader
     * @param serverCall server call
     * @param headers    server call headers
     * @return transaction, or {@literal null} if none has been created
     */
    @Nullable
    public Transaction<?> startTransaction(Tracer tracer, ClassLoader cl, ServerCall<?, ?> serverCall, Metadata headers) {
        MethodDescriptor<?, ?> methodDescriptor = serverCall.getMethodDescriptor();

        // ignore non-unary method calls for now
        if (methodDescriptor.getType() != MethodDescriptor.MethodType.UNARY) {
            return null;
        }

        if (tracer.getActive() != null) {
            // don't create nested transactions for nested calls
            // this might be something to do on tracer level instead
            return null;
        }

        Transaction<?> transaction = tracer.startChildTransaction(headers, headerGetter, cl);
        if (transaction == null) {
            return null;
        }

        transaction.withName(methodDescriptor.getFullMethodName())
            .withType("request")
            .setFrameworkName(FRAMEWORK_NAME);

        return transaction.activate();
    }

    /**
     * Registers transaction for lookup with both {@link ServerCall} and {@link ServerCall.Listener} as keys, transaction
     * is expected to be activated before and is de-activated by this method.
     *
     * @param serverCall  server call
     * @param listener    server call listener
     * @param transaction transaction
     */
    public void registerTransaction(ServerCall<?, ?> serverCall, ServerCall.Listener<?> listener, Transaction<?> transaction) {
        serverCallTransactions.put(serverCall, transaction);
        serverListenerTransactions.put(listener, transaction);
        transaction.deactivate();
    }

    /**
     * Sets transaction status using a transaction lookup by {@link ServerCall}, also removes lookup entry as it not
     * used afterwards.
     *
     * @param status     status
     * @param thrown     thrown exception (if any)
     * @param serverCall server call
     */
    public void exitServerCall(Status status, @Nullable Throwable thrown, ServerCall<?, ?> serverCall) {
        Transaction<?> transaction = serverCallTransactions.remove(serverCall);

        if (transaction != null) {
            // there are multiple ways to terminate transaction, which aren't mutually exclusive
            // thus we have to check if outcome has already been set to keep the first:
            // 1. thrown exception within any of ServerCall.Listener methods
            // 2. ServerCall.onClose, which might falsely report 'OK' status after a thrown listener exception.
            //    in this case we just have to ignore the reported status if already set
            if (Outcome.UNKNOWN == transaction.getOutcome()) {
                transaction.withOutcome(toServerOutcome(status))
                    .withResultIfUnset(status.getCode().name()); // keep outcome and result consistent
            }
            transaction.captureException(thrown);
            if (thrown != null) {
                // transaction ended due to an exception, we have to end it
                transaction.end();
            }
        }
    }

    public static Outcome toClientOutcome(@Nullable Status status) {
        if (status == null || !status.isOk()) {
            return Outcome.FAILURE;
        } else {
            return Outcome.SUCCESS;
        }
    }

    public static Outcome toServerOutcome(@Nullable Status status) {
        if (status == null) {
            return Outcome.FAILURE;
        }
        switch (status.getCode()) {
            case UNKNOWN:
            case DEADLINE_EXCEEDED:
            case RESOURCE_EXHAUSTED:
            case FAILED_PRECONDITION:
            case ABORTED:
            case INTERNAL:
            case UNAVAILABLE:
            case DATA_LOSS:
                return Outcome.FAILURE;
            default:
                return Outcome.SUCCESS;
        }
    }

    /**
     * Activates transaction on starting server call listener method
     *
     * @param listener server call listener
     * @return transaction, or {@literal null} if there is none
     */
    @Nullable
    public Transaction<?> enterServerListenerMethod(ServerCall.Listener<?> listener) {
        Transaction<?> transaction = serverListenerTransactions.get(listener);
        if (transaction != null) {
            transaction.activate();
        }
        return transaction;
    }

    /**
     * Deactivates (and terminates) transaction on ending server call listener method
     *
     * @param thrown          thrown exception
     * @param listener        server call listener
     * @param transaction     transaction
     * @param terminateStatus status to use to terminate transaction, will not terminate it if {@literal null}
     */
    public void exitServerListenerMethod(@Nullable Throwable thrown,
                                         ServerCall.Listener<?> listener,
                                         @Nullable Transaction<?> transaction,
                                         @Nullable Status terminateStatus) {

        if (transaction == null) {
            return;
        }

        transaction
            .captureException(thrown)
            .deactivate();

        if (thrown == null && terminateStatus == null) {
            return;
        }

        boolean setTerminateStatus = false;
        if (null != thrown) {
            // when there is a runtime exception thrown in one of the listener methods the calling code will catch it
            // and make this the last listener method called
            terminateStatus = Status.fromThrowable(thrown);
            setTerminateStatus = true;

        } else if (transaction.getOutcome() == Outcome.UNKNOWN) {
            setTerminateStatus = true;
        }

        if (setTerminateStatus) {
            transaction.withResultIfUnset(terminateStatus.getCode().name());
            transaction.withOutcome(toServerOutcome(terminateStatus));
        }

        transaction.end();
        serverListenerTransactions.remove(listener);
    }

    // exit span management (client part)

    /**
     * Called at the entry to {@link io.grpc.Channel#newCall(MethodDescriptor, CallOptions)}.
     * Starts a client exit span.
     * <br>
     * This is the first method called during a client call execution, the next is {@link #onClientCallCreationExit(ClientCall, Span)}.
     *
     * @param parent    parent transaction, or parent span provided by {@link Tracer#getActive()}.
     * @param method    method descriptor
     * @param authority channel authority string (host+port)
     * @return client call span (activated) or {@literal null} if not within an exit span.
     */
    @Nullable
    public Span<?> onClientCallCreationEntry(@Nullable AbstractSpan<?> parent,
                                          @Nullable MethodDescriptor<?, ?> method,
                                          @Nullable String authority) {

        if (null == parent) {
            return null;
        }

        // we only support unary method calls and ignore others for now
        if (method != null && method.getType() != MethodDescriptor.MethodType.UNARY) {
            return null;
        }

        Span<?> span = parent.createExitSpan();
        if (span == null) {
            // as it's an external call, we only need a single span for nested calls
            return null;
        }

        span.withName(method == null ? null : method.getFullMethodName())
            .withType("external")
            .withSubtype(GRPC);

        span.getContext().getDestination()
            .withAddressPort(authority);

        span.getContext().getServiceTarget()
            .withType(GRPC)
            .withName(authority)
            .withNameOnlyDestinationResource();

        return span.activate();
    }

    /**
     * Called at the exit from {@link io.grpc.Channel#newCall(MethodDescriptor, CallOptions)}.
     * Registers (and deactivates) span in internal storage for lookup by client call.
     * <br>
     * This is the 2nd method called during client call execution, the next is {@link #clientCallStartEnter(ClientCall, ClientCall.Listener, Metadata)}.
     *
     * @param clientCall    client call
     * @param spanFromEntry span created at {@link #onClientCallCreationEntry(AbstractSpan, MethodDescriptor, String)}
     */
    public void onClientCallCreationExit(@Nullable ClientCall<?, ?> clientCall, @Nullable Span<?> spanFromEntry) {
        if (clientCall != null) {
            Span<?> spanToMap = spanFromEntry;
            if (spanToMap == null) {
                // handling nested newCall() invocations - we still want to map the client call to the same span
                AbstractSpan<?> active = GlobalTracer.get().getActive();
                if (active instanceof Span<?>) {
                    Span<?> tmp = (Span<?>) active;
                    if (tmp.getSubtype() != null && tmp.getSubtype().equals(GRPC) && tmp.isExit()) {
                        spanToMap = tmp;
                    }
                }
            }

            if (spanToMap != null && !spanToMap.isDiscarded()) {
                // io.grpc.internal.DelayedClientCall was introduced in 1.32 as a temporary placeholder for client calls
                // that eventually refer to the real client call, but when they are first created
                if (isDelayedClientCall(clientCall)) {
                    delayedClientCallSpans.put(clientCall, spanToMap);
                } else {
                    clientCallSpans.put(clientCall, spanToMap);
                }
            }
        }

        if (spanFromEntry != null) {
            spanFromEntry.deactivate();
        }
    }

    private boolean isDelayedClientCall(ClientCall<?, ?> clientCall) {
        Class<?> clientCallClass = clientCall.getClass();
        return clientCallClass.getName().equals("io.grpc.internal.DelayedClientCall") ||
            clientCallClass.getSuperclass().getName().equals("io.grpc.internal.DelayedClientCall");
    }

    /**
     * Fixes the span mapping for the "real" client call based on the span that is already mapped to the corresponding
     * placeholder {@code io.grpc.internal.DelayedClientCall}.
     * <br>
     * This replacement must take into account two options:
     * <ul>
     *     <li>
     *         a span was created for each the placeholder and the real call, in which case we use the placeholder span
     *         (which was created first) and discard the one created for the real call
     *     </li>
     *     <li>
     *         a single span was created and either mapped for both the placeholder and the real call (theoretical),
     *         in which case we need to do nothing, or mapped only to the placeholder, in which case we need to add a
     *         span mapping for the real client call
     *     </li>
     * </ul>
     *
     * @param placeholderClientCall may be created as part of the gRPC channel implementation to be replaced later with
     *                              the real client call
     * @param realClientCall        the client call instance that represents the actual client call
     */
    public void replaceClientCallRegistration(ClientCall<?, ?> placeholderClientCall, ClientCall<?, ?> realClientCall) {
        // we cannot remove yet, because the span could have been ended already through ClientCall#start(), in which case
        // it will be recycled ahead of time due to reference decrement when removed from the map
        Span<?> spanOfPlaceholder = delayedClientCallSpans.get(placeholderClientCall);
        if (spanOfPlaceholder == null) {
            return;
        }

        try {
            // we cannot remove yet, because the span could have been ended already, in which case
            // it will be recycled ahead of time due to reference decrement when removed from the map
            Span<?> spanOfRealClientCall = clientCallSpans.get(realClientCall);
            boolean mapPlaceholderSpanToRealClientCall = false;
            if (spanOfRealClientCall == null) {
                mapPlaceholderSpanToRealClientCall = true;
            } else if (spanOfRealClientCall != spanOfPlaceholder) {
                // the placeholder span is the one we want to use, we need to discard the real call span
                if (!spanOfRealClientCall.isFinished()) {
                    spanOfRealClientCall
                        .requestDiscarding()
                        // no need to deactivate
                        .end();
                }
                // the discarded span will be removed when replaced with the correct span
                mapPlaceholderSpanToRealClientCall = true;
            } else if (spanOfRealClientCall.isFinished()) {
                // the real client call is already mapped to the correct span, but it is already ended, so needs to be removed
                clientCallSpans.remove(realClientCall);
            }

            if (mapPlaceholderSpanToRealClientCall && !spanOfPlaceholder.isFinished()) {
                clientCallSpans.put(realClientCall, spanOfPlaceholder);
            }
        } finally {
            delayedClientCallSpans.remove(placeholderClientCall);
        }
    }

    /**
     * Starts client call and switch to client call listener instrumentation.
     * <br>
     * This is the 3rd method called during client call execution, the next in sequence is
     * {@link #clientCallStartExit(Span, ClientCall.Listener, Throwable)}.
     *
     * @param clientCall client call
     * @param listener   client call listener
     * @param headers    headers
     * @return span, or {@literal null is there is none}
     */
    @Nullable
    public Span<?> clientCallStartEnter(ClientCall<?, ?> clientCall,
                                        ClientCall.Listener<?> listener,
                                        Metadata headers) {

        // span should already have been registered
        // no other lookup by client call is required, thus removing entry
        Span<?> span = clientCallSpans.remove(clientCall);
        if (span == null) {
            return null;
        }

        clientCallListenerSpans.put(listener, span);

        if (!HeaderUtils.containsAny(tracer.getTraceHeaderNames(), headers, headerGetter)) {
            span.propagateTraceContext(headers, headerSetter);
        }

        return span.activate();
    }

    /**
     * Performs client call start cleanup in case of exception
     *
     * @param spanFromEntry span created by {@link #clientCallStartEnter(ClientCall, ClientCall.Listener, Metadata)}
     * @param listener      client call listener
     * @param thrown        thrown exception
     */
    public void clientCallStartExit(@Nullable Span<?> spanFromEntry, ClientCall.Listener<?> listener, @Nullable Throwable thrown) {
        if (spanFromEntry != null) {
            spanFromEntry.deactivate();
        }
        if (thrown != null) {
            // when there is an exception, we have to end span and perform some cleanup
            clientCallListenerSpans.remove(listener);
            if (spanFromEntry != null) {
                spanFromEntry.withOutcome(Outcome.FAILURE)
                    .end();
            }
        }
    }

    public void cancelCall(ClientCall<?, ?> clientCall, @Nullable Throwable cause) {
        WeakMap<ClientCall<?, ?>, Span<?>> clientCallMap = (isDelayedClientCall(clientCall)) ? delayedClientCallSpans : clientCallSpans;
        // we can't remove yet, in order to avoid reference decrement prematurely
        Span<?> span = clientCallMap.get(clientCall);
        if (span != null) {
            if (!span.isFinished()) {
                span
                    .captureException(cause)
                    .withOutcome(toClientOutcome(Status.CANCELLED))
                    .end();
            }
            clientCallMap.remove(clientCall);
        }
    }

    /**
     * Lookup and activate span when entering listener method execution
     *
     * @param listener client call listener
     * @return active span or {@literal null} if there is none
     */
    @Nullable
    public Span<?> enterClientListenerMethod(ClientCall.Listener<?> listener) {
        Span<?> span = clientCallListenerSpans.get(listener);
        if (span != null) {
            if (span.isFinished()) {
                // the span may have already been ended by another listener on a different thread/stack
                clientCallListenerSpans.remove(listener);
                span = null;
            } else if (span == GlobalTracer.get().getActive()) {
                // avoid duplicated activation and invocation on nested listener method calls
                span = null;
            } else {
                span.activate();
            }
        }
        return span;
    }

    /**
     * De-activates active span when exiting listener method execution, optionally terminates span when required.
     *
     * @param thrown        thrown exception (if any)
     * @param listener      client call listener
     * @param span          span reference obtained from {@link #enterClientListenerMethod(ClientCall.Listener)}
     * @param onCloseStatus status if method is {@code onClose(...)}, {@literal null} otherwise
     */
    public void exitClientListenerMethod(@Nullable Throwable thrown,
                                         ClientCall.Listener<?> listener,
                                         @Nullable Span<?> span,
                                         @Nullable Status onCloseStatus) {

        boolean lastCall = onCloseStatus != null || thrown != null;

        if (span != null) {
            span.captureException(thrown)
                .deactivate();

            if (lastCall) {
                // span needs to be ended when last listener method is called or on the 1st thrown exception
                span.withOutcome(toClientOutcome(onCloseStatus))
                    .end();
            }
        }

        if (lastCall) {
            clientCallListenerSpans.remove(listener);
        }
    }

    private class GrpcHeaderSetter implements TextHeaderSetter<Metadata> {

        @Override
        public void setHeader(String headerName, String headerValue, Metadata carrier) {
            carrier.put(getHeader(headerName), headerValue);
        }
    }

    private class GrpcHeaderGetter extends AbstractHeaderGetter<String, Metadata> implements TextHeaderGetter<Metadata> {

        @Nullable
        @Override
        public String getFirstHeader(String headerName, Metadata carrier) {
            return carrier.get(getHeader(headerName));
        }

    }

    private Metadata.Key<String> getHeader(String headerName) {
        Metadata.Key<String> key = headerCache.get(headerName);
        if (key == null) {
            key = Metadata.Key.of(headerName, Metadata.ASCII_STRING_MARSHALLER);
            headerCache.put(headerName, key);
        }
        return key;
    }
}
