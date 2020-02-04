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
package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import io.grpc.ClientCall;

import javax.annotation.Nullable;

/**
 * Helper class for gRPC client and server calls.
 *
 * <br>
 * Since helper class is loaded in the bootstrap classloader, we have to make sure that this class does not import any gRPC class.
 * {@see co.elastic.apm.agent.bci.HelperClassManager} for more details about this
 */
public class GrpcHelper {

    /**
     * Map of all in-flight spans, is only used by client part.
     * Key is {@link ClientCall}, but refered as {@link Object} to avoid loading any gRPC reference in the bootstrap classloader
     */
    private static final WeakConcurrentMap<Object, Span> inFlightSpans = new WeakConcurrentMap.WithInlinedExpunction<Object, Span>();

    private static final String GRPC = "grpc";

    // transaction management (server part)

    @VisibleForAdvice
    public static void startTransaction(ElasticApmTracer tracer, ClassLoader cl, String methodName, String header) {
        tracer.startTransaction(TraceContext.fromTraceparentHeader(), header, cl)
            .withName(methodName)
            .withType("request")
            .activate();
    }


    @VisibleForAdvice
    public static void endTransaction(String status, @Nullable Throwable thrown, @Nullable Transaction transaction) {
        if (transaction == null || transaction.getResult() != null) {
            return;
        }

        // transaction might be terminated early in case of thrown exception
        // from method signature it's a runtime exception, thus very likely an issue in server implementation
        transaction
            .withResult(status)
            .captureException(thrown)
            .deactivate()
            .end();
    }

    // exit span management (client part)

    @Nullable
    @VisibleForAdvice
    public static Span createExitSpanAndActivate(@Nullable Transaction transaction, @Nullable String methodName) {
        Span span;
        if (null == transaction) {
            return null;
        }

        span = transaction.createExitSpan();
        if (span == null) {
            // as it's an external call, we only need a single span for nested calls
            return null;
        }

        return span.withName(methodName)
            .withType("external")
            .withSubtype(GRPC)
            .activate();
    }

    @VisibleForAdvice
    public static void registerSpanAndDeactivate(@Nullable Span span, Object clientCall) {
        if (span != null) {
            inFlightSpans.put(clientCall, span);
            span.deactivate();
        }
    }

    @VisibleForAdvice
    public static void startSpan(Object clientCall) {
        Span span = inFlightSpans.get(clientCall);
        if (span == null) {
            return;
        }
        span.setStartTimestampNow();
        span.activate();
    }

    @VisibleForAdvice
    public static void endSpan(Object clientCall) {
        Span span = inFlightSpans.get(clientCall);
        if (span == null) {
            return;
        }

        span.deactivate()
            .end();
    }

    @VisibleForAdvice
    public static void enrichSpanContext(Object clientCall, @Nullable String authority) {
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


}
