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
import io.grpc.MethodDescriptor;
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
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments gRPC client calls by relying on {@link ClientCall} internal implementation {@code io.grpc.internal.ClientCallImpl},
 * which provides the full client call lifecycle in a single object instance.
 * <br>
 * <p>
 * full call lifecycle is split in few sub-implementations:
 * <ul>
 *     <li>{@link Constructor} for constructor to get method call name</li>
 *     <li>{@link Start} for client span start</li>
 *     <li>{@link ListenerClose} for {@link ClientCall.Listener#onClose} for client span end</li>
 *     <li>{@link ListenerMessage} for {@link ClientCall.Listener#onMessage}</li>
 *     <li>{@link ListenerHeaders} for {@link ClientCall.Listener#onHeaders}</li>
 *     <li>{@link ListenerReady} for {@link ClientCall.Listener#onReady}</li>
 * </ul>
 */
public abstract class ClientCallImplInstrumentation extends BaseInstrumentation {

    @VisibleForAdvice
    public static final Collection<Class<? extends ElasticApmInstrumentation>> RESPONSE_LISTENER_INSTRUMENTATIONS = Arrays.<Class<? extends ElasticApmInstrumentation>>asList(
        ListenerClose.class,
        ListenerReady.class,
        ListenerMessage.class,
        ListenerHeaders.class
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
     * Instruments {@code ClientCallImpl} constructor to build client call exit span. Span is kept activated during
     * constructor execution which makes sure that any nested constructor call will only create one exit span.
     */
    public static class Constructor extends ClientCallImplInstrumentation {

        public Constructor(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor().and(takesArgument(0, MethodDescriptor.class));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(@Nullable @Advice.Argument(0) MethodDescriptor<?, ?> method,
                                    @Advice.Local("span") Span span) {

            if (tracer == null || grpcHelperManager == null) {
                return;
            }

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(MethodDescriptor.class);
            if (helper != null) {
                span = helper.createExitSpanAndActivate(tracer.currentTransaction(), method);
            }

        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void onExit(@Advice.This ClientCall<?, ?> clientCall,
                                   @Advice.Local("span") @Nullable Span span) {

            if (tracer == null || grpcHelperManager == null) {
                return;
            }

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(MethodDescriptor.class);
            if (helper != null) {
                helper.registerSpanAndDeactivate(span, clientCall);
            }
        }
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
                                    @Advice.Argument(1) Metadata headers) {

            if (tracer == null || grpcHelperManager == null) {
                return;
            }

            // For asynchronous calls, the call to the server is 'half closed' just after sending the message.
            // For synchronous calls, the call to the server is 'half closed' when we get the response.
            // Thus we should instead register for the response listener to properly terminate the span,
            // using the same strategy also works for the synchronous calls.

            ElasticApmAgent.ensureInstrumented(listener.getClass(), RESPONSE_LISTENER_INSTRUMENTATIONS);

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.class);
            if (helper != null) {
                helper.startSpan(clientCall, listener, headers);
            }

        }

    }

    // response listener instrumentation

    public static abstract class ListenerInstrumentation extends BaseInstrumentation {

        private final String methodName;

        protected ListenerInstrumentation(ElasticApmTracer tracer, String methodName) {
            super(tracer);
            this.methodName = methodName;
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named(methodName);
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
     * Instruments {@link ClientCall.Listener#onMessage(Object)} to capture any thrown exception and end current span
     */
    public static class ListenerClose extends ListenerInstrumentation {

        public ListenerClose(ElasticApmTracer tracer) {
            super(tracer, "onClose");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                   @Advice.Thrown Throwable thrown) {

            if (tracer == null || grpcHelperManager == null) {
                return;
            }

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.Listener.class);
            if (helper != null) {
                helper.endSpan(listener, thrown);
            }

        }

    }

    /**
     * Instruments {@link ClientCall.Listener#onMessage(Object)} to capture any thrown exception.
     */
    public static class ListenerMessage extends ListenerInstrumentation {

        public ListenerMessage(ElasticApmTracer tracer) {
            super(tracer, "onMessage");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                   @Advice.Thrown @Nullable Throwable thrown) {

            if (tracer == null || grpcHelperManager == null) {
                return;
            }

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.Listener.class);
            if (helper != null) {
                helper.captureListenerException(listener, thrown);
            }

        }
    }

    /**
     * Instruments {@link ClientCall.Listener#onHeaders} to capture any thrown exception.
     */
    public static class ListenerHeaders extends ListenerInstrumentation {

        public ListenerHeaders(ElasticApmTracer tracer) {
            super(tracer, "onHeaders");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                   @Advice.Thrown @Nullable Throwable thrown) {

            if (tracer == null || grpcHelperManager == null) {
                return;
            }

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.Listener.class);
            if (helper != null) {
                helper.captureListenerException(listener, thrown);
            }

        }
    }

    /**
     * Instruments {@link ClientCall.Listener#onReady} to capture any thrown exception.
     */
    public static class ListenerReady extends ListenerInstrumentation {

        public ListenerReady(ElasticApmTracer tracer) {
            super(tracer, "onReady");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                   @Advice.Thrown @Nullable Throwable thrown) {

            if (tracer == null || grpcHelperManager == null) {
                return;
            }

            GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.Listener.class);
            if (helper != null) {
                helper.captureListenerException(listener, thrown);
            }

        }
    }

}
