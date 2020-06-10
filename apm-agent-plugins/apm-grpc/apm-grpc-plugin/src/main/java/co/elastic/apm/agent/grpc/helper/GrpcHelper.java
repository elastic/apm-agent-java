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

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;

import javax.annotation.Nullable;

@VisibleForAdvice
public interface GrpcHelper {

    String GRPC = "grpc";

    // server part

    /**
     * Starts transaction and registers for lookup with both {@link ServerCall} and {@link ServerCall.Listener} as keys.
     *
     * @param tracer     tracer
     * @param cl         classloader
     * @param serverCall server call
     * @param headers    server call headers
     * @param listener   server call listener
     */
    void startAndRegisterTransaction(ElasticApmTracer tracer,
                                     ClassLoader cl,
                                     ServerCall<?, ?> serverCall,
                                     Metadata headers,
                                     ServerCall.Listener<?> listener);

    /**
     * Sets transaction status using a transaction lookup by {@link ServerCall}, also removes lookup entry as it not
     * used afterwards.
     *
     * @param status     status
     * @param thrown     thrown exception (if any)
     * @param serverCall server call
     */
    void setTransactionStatus(Status status,
                              @Nullable Throwable thrown,
                              ServerCall<?, ?> serverCall);

    /**
     * Activates transaction on starting server call listener method
     *
     * @param listener server call listener
     * @return transaction, or {@literal null} if there is none
     */
    @Nullable
    Transaction enterServerListenerMethod(ServerCall.Listener<?> listener);

    /**
     * Deactivates (and terminates) transaction on ending server call listener method
     *
     * @param thrown       thrown exception
     * @param listener     server call listener
     * @param transaction  transaction
     * @param isLastMethod {@literal true} if listener method should terminate transaction
     */
    void exitServerListenerMethod(@Nullable Throwable thrown,
                                  ServerCall.Listener<?> listener,
                                  @Nullable Transaction transaction,
                                  boolean isLastMethod);

    // client part

    /**
     * Starts a client exit span.
     * <br>
     * This is the first method called during a client call execution, the next is {@link #registerSpan(ClientCall, Span)}.
     *
     * @param parent    parent transaction, or parent span provided by {@link ElasticApmTracer#getActive()}.
     * @param method    method descriptor
     * @param authority channel authority string (host+port)
     * @return client call span (activated) or {@literal null} if not within an exit span.
     */
    @Nullable
    Span startSpan(@Nullable AbstractSpan<?> parent,
                   @Nullable MethodDescriptor<?, ?> method,
                   @Nullable String authority);

    /**
     * Registers (and deactivates) span in internal storage for lookup by client call.
     * <br>
     * This is the 2cnd method called during client call execution, the next is {@link #clientCallStart(ClientCall, ClientCall.Listener, Metadata)}.
     *
     * @param clientCall client call
     * @param span       span
     */
    void registerSpan(@Nullable ClientCall<?, ?> clientCall, Span span);

    /**
     * Starts client call and switch to client call listener instrumentation.
     * <br>
     * This is the 3rd method called during client call execution and the last to involve client call.
     *
     * @param clientCall client call
     * @param listener   client call listener
     * @param headers    headers
     */
    void clientCallStart(ClientCall<?, ?> clientCall,
                         ClientCall.Listener<?> listener,
                         Metadata headers);


    @Deprecated
    void captureListenerException(ClientCall.Listener<?> responseListener, @Nullable Throwable thrown);

    @Nullable
    Span enterClientListenerMethod(ClientCall.Listener<?> listener);

    void exitClientListenerMethod(@Nullable Throwable thrown,
                                  ClientCall.Listener<?> listener,
                                  @Nullable Span span,
                                  boolean isLastMethod);

}
