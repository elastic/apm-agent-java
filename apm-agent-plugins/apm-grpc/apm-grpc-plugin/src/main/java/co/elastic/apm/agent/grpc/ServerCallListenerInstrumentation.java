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

import co.elastic.apm.agent.impl.transaction.Transaction;
import io.grpc.ServerCall;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments implementations of {@link ServerCall.Listener} for runtime exceptions and transaction activation
 * <br>
 * Implementation is split in two classes {@link FinalMethodCall} and {@link NonFinalMethodCall}
 * <ul>
 *     <li>{@link ServerCall.Listener#onReady()}} ({@link NonFinalMethodCall})</li>
 *     <li>{@link ServerCall.Listener#onMessage(Object)} ({@link NonFinalMethodCall})</li>
 *     <li>{@link ServerCall.Listener#onHalfClose()} ({@link NonFinalMethodCall})</li>
 *     <li>{@link ServerCall.Listener#onCancel()} ({@link FinalMethodCall})</li>
 *     <li>{@link ServerCall.Listener#onComplete()} ({@link FinalMethodCall})</li>
 * </ul>
 */
public abstract class ServerCallListenerInstrumentation extends BaseInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // pre-filtering is used to make this quite fast and limited to gRPC packages
        return hasSuperType(named("io.grpc.ServerCall$Listener"));
    }

    /**
     * Instruments implementations of {@link ServerCall.Listener}
     * <ul>
     *     <li>{@link ServerCall.Listener#onReady()}()}</li>
     *     <li>{@link ServerCall.Listener#onMessage(Object)}</li>
     *     <li>{@link ServerCall.Listener#onHalfClose()}</li>
     * </ul>
     */
    public static class NonFinalMethodCall extends ServerCallListenerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("onReady")
                //
                // message received --> indicates RPC start for unary call
                // actual method invocation is delayed until 'half close'
                .or(named("onMessage"))
                //
                // client completed all message sending, but can still cancel the call
                // --> for unary calls, actual method invocation is done within 'onHalfClose' method.
                .or(named("onHalfClose"));
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This ServerCall.Listener<?> listener) {
            return helper.enterServerListenerMethod(listener);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                                  @Advice.This ServerCall.Listener<?> listener,
                                  @Advice.Enter @Nullable Object transaction) {

            if (transaction instanceof Transaction) {
                helper.exitServerListenerMethod(thrown, listener, (Transaction) transaction, false);
            }
        }
    }

    /**
     * Instruments implementations of {@link ServerCall.Listener}
     * <ul>
     *     <li>{@link ServerCall.Listener#onCancel()}</li>
     *     <li>{@link ServerCall.Listener#onComplete()}</li>
     * </ul>
     * <p>
     * If one of those methods is called, the other one is guaranteed to not be called, hence the 'final'.
     */
    public static class FinalMethodCall extends ServerCallListenerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            // call cancelled by client (or network issue)
            // --> end of unary call (error)
            return named("onCancel")
                //
                // call complete (but client not guaranteed to get all messages)
                // --> end of unary call (success)
                .or(named("onComplete"));
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This ServerCall.Listener<?> listener) {
            return helper.enterServerListenerMethod(listener);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                                  @Advice.This ServerCall.Listener<?> listener,
                                  @Advice.Enter @Nullable Object transaction) {

            if (transaction instanceof Transaction) {
                helper.exitServerListenerMethod(thrown, listener, (Transaction) transaction, true);
            }
        }
    }

}
