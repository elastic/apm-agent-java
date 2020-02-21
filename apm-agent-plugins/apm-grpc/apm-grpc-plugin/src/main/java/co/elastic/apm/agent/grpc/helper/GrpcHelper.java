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

    void startTransaction(ElasticApmTracer tracer, ClassLoader cl, ServerCall<?, ?> serverCall, Metadata headers);

    void endTransaction(Status status, @Nullable Throwable thrown, @Nullable Transaction transaction);

    // client part

    @Nullable
    Span createExitSpanAndActivate(@Nullable Transaction transaction, @Nullable MethodDescriptor<?, ?> method);

    void registerSpanAndDeactivate(@Nullable Span span, ClientCall<?, ?> clientCall);

    void startSpan(ClientCall<?, ?> clientCall, ClientCall.Listener<?> responseListener, Metadata headers);

    void endSpan(ClientCall.Listener<?> responseListener, @Nullable Throwable thrown);

    void captureListenerException(ClientCall.Listener<?> responseListener, @Nullable Throwable thrown);

    void enrichSpanContext(ClientCall<?, ?> clientCall, @Nullable String authority);
}
