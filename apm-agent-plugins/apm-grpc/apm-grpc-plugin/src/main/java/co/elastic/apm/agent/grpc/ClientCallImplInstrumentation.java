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

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.grpc.helper.GrpcHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments gRPC client calls by relying on {@link ClientCall} internal implementation {@code io.grpc.internal.ClientCallImpl},
 * which provides the full client call lifecycle in a single object instance.
 * <br>
 * <p>
 * full call lifecycle is split in few sub-implementations:
 * <ul>
 *     <li>{@link Start} for client span start</li>
 *     <li>{@link ListenerClose} for {@link ClientCall.Listener#onClose} for client span end</li>
 *     <li>{@link OtherListenerMethod} for other methods of {@link ClientCall.Listener}.
 * </ul>
 */
public abstract class ClientCallImplInstrumentation extends BaseInstrumentation {

    @VisibleForAdvice
    public static final Collection<Class<? extends ElasticApmInstrumentation>> RESPONSE_LISTENER_INSTRUMENTATIONS = Arrays.<Class<? extends ElasticApmInstrumentation>>asList(
        ListenerClose.class,
        OtherListenerMethod.class
    );

    public ClientCallImplInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("io.grpc")
            // matches all the implementations of ClientCall available in io.grpc package
            .and(nameEndsWith("ClientCallImpl"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // pre-filtering is used to make this quite fast and limited to gRPC packages
        return hasSuperType(named("io.grpc.ClientCall"));
    }

    /**
     * Instruments {@code ClientCallImpl#start} to start client call span
     */
    public static class Start extends ClientCallImplInstrumentation {

        public Start(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("start");
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(@Advice.This ClientCall<?, ?> clientCall,
                                    @Advice.Argument(0) ClientCall.Listener<?> listener,
                                    @Advice.Argument(1) Metadata headers,
                                    @Advice.Local("span") Span span) {

            if (grpcHelperManager == null) {
                return;
            }

            ElasticApmAgent.ensureInstrumented(listener.getClass(), RESPONSE_LISTENER_INSTRUMENTATIONS);

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.class);
            if (helper != null) {
                span = helper.clientCallStartEnter(clientCall, listener, headers);
            }

        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.Argument(0) ClientCall.Listener<?> listener,
                                   @Advice.Thrown @Nullable Throwable thrown,
                                   @Advice.Local("span") @Nullable Span span) {

            if (span == null) {
                return;
            }
            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.class);
            if (helper != null) {
                helper.clientCallStartExit(listener, thrown);
            }
        }

    }

    // response listener instrumentation

    public static abstract class ListenerInstrumentation extends BaseInstrumentation {

        protected ListenerInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        /**
         * Overridden in {@link ElasticApmAgent#ensureInstrumented(Class, Collection)},
         * based on the type of the {@linkplain ClientCall.Listener} implementation class.
         */
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return any();
        }

    }

    /**
     * Instruments {@link ClientCall.Listener#onClose(Status, Metadata)} )} to capture any thrown exception and end current span
     */
    public static class ListenerClose extends ListenerInstrumentation {

        public ListenerClose(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("onClose");
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(@Advice.This ClientCall.Listener<?> listener,
                                    @Advice.Local("span") Span span) {

            if (grpcHelperManager == null) {
                return;
            }

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.Listener.class);
            if (helper != null) {
                span = helper.enterClientListenerMethod(listener);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                   @Advice.Thrown @Nullable Throwable thrown,
                                   @Advice.Local("span") @Nullable Span span) {

            if (grpcHelperManager == null || span == null) {
                return;
            }

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.Listener.class);
            if (helper != null) {
                helper.exitClientListenerMethod(thrown, listener, span, true);
            }

        }

    }

    /**
     * Generic call listener method to handle span activation and capturing exceptions.
     * Instruments:
     * <ul>
     *     <li>{@link ClientCall.Listener#onMessage(Object)}</li>
     *     <li>{@link ClientCall.Listener#onHeaders}</li>
     *     <li>{@link ClientCall.Listener#onReady}</li>
     * </ul>
     */
    public static class OtherListenerMethod extends ListenerInstrumentation {

        public OtherListenerMethod(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("onMessage")
                .or(named("onHeaders"))
                .or(named("onReady"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(@Advice.This ClientCall.Listener<?> listener,
                                    @Advice.Local("span") Span span) {

            if (grpcHelperManager == null) {
                return;
            }

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.Listener.class);
            if (helper != null) {
                span = helper.enterClientListenerMethod(listener);
            }
        }


        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                   @Advice.Thrown Throwable thrown,
                                   @Advice.Local("span") @Nullable Span span) {

            if (grpcHelperManager == null || span == null) {
                return;
            }

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.Listener.class);
            if (helper != null) {
                helper.exitClientListenerMethod(thrown, listener, span, false);
            }
        }

    }

}
