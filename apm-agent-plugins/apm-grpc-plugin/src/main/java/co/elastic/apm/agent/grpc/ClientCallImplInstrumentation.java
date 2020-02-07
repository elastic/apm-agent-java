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
import co.elastic.apm.agent.impl.transaction.Span;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
    public static final List<Class<? extends ElasticApmInstrumentation>> RESPONSE_LISTENER_INSTRUMENTATIONS = Arrays.asList(
        ListenerClose.class,
        ListenerReady.class,
        ListenerMessage.class,
        ListenerHeaders.class
    );

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

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor().and(takesArgument(0, MethodDescriptor.class));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(
            @Nullable @Advice.Argument(0) MethodDescriptor<?, ?> method,
            @Advice.Local("span") Span span) {

            if (tracer == null) {
                return;
            }

            String methodName = method == null ? null : method.getFullMethodName();

            span = GrpcHelper.createExitSpanAndActivate(tracer.currentTransaction(), methodName);
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void onExit(@Advice.This ClientCall<?, ?> clientCall,
                                   @Advice.Local("span") @Nullable Span span) {

            GrpcHelper.registerSpanAndDeactivate(span, clientCall);
        }
    }

    /**
     * Instruments {@code ClientCallImpl#start} to start client call span
     */
    public static class Start extends ClientCallImplInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("start");
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(@Advice.This ClientCall<?, ?> clientCall, @Advice.Argument(value = 0) ClientCall.Listener<?> listener) {
            // For asynchronous calls, the call to the server is 'half closed' just after sending the message.
            // For synchronous calls, the call to the server is 'half closed' when we get the response.
            // Thus we should instead register for the response listener to properly terminate the span,
            // using the same strategy also works for the synchronous calls.

            ElasticApmAgent.ensureInstrumented(listener.getClass(), RESPONSE_LISTENER_INSTRUMENTATIONS);

            GrpcHelper.startSpan(clientCall, listener);
        }

    }

    // response listener instrumentation

    public static abstract class ListenerInstrumentation extends ElasticApmInstrumentation {

        private final String methodName;

        protected ListenerInstrumentation(String methodName) {
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

        @Override
        public final Collection<String> getInstrumentationGroupNames() {
            return GrpcHelper.GRPC_GROUP;
        }

    }

    /**
     * Instruments {@link ClientCall.Listener#onMessage(Object)} to capture any thrown exception and end current span
     */
    public static class ListenerClose extends ListenerInstrumentation {

        public ListenerClose() {
            super("onClose");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                   @Advice.Thrown Throwable thrown) {

            GrpcHelper.endSpan(listener, thrown);
        }

    }

    /**
     * Instruments {@link ClientCall.Listener#onMessage(Object)} to capture any thrown exception.
     */
    public static class ListenerMessage extends ListenerInstrumentation {

        public ListenerMessage() {
            super("onMessage");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                   @Advice.Thrown @Nullable Throwable thrown) {

            GrpcHelper.captureListenerException(listener, thrown);
        }
    }

    /**
     * Instruments {@link ClientCall.Listener#onHeaders} to capture any thrown exception.
     */
    public static class ListenerHeaders extends ListenerInstrumentation {

        public ListenerHeaders() {
            super("onHeaders");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                   @Advice.Thrown @Nullable Throwable thrown) {

            GrpcHelper.captureListenerException(listener, thrown);
        }
    }

    /**
     * Instruments {@link ClientCall.Listener#onReady} to capture any thrown exception.
     */
    public static class ListenerReady extends ListenerInstrumentation {

        public ListenerReady() {
            super("onReady");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                   @Advice.Thrown @Nullable Throwable thrown) {

            GrpcHelper.captureListenerException(listener, thrown);
        }
    }

}
