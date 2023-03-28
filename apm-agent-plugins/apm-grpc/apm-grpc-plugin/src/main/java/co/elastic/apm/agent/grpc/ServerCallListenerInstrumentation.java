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

import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.sdk.DynamicTransformer;
import io.grpc.ServerCall;
import io.grpc.Status;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments implementations of {@link ServerCall.Listener} for runtime exceptions and transaction activation
 * <br>
 * Implementation is split in 3 classes
 * <ul>
 *     <li>{@link ServerCall.Listener#onReady()}} ({@link OtherMethod})</li>
 *     <li>{@link ServerCall.Listener#onMessage(Object)} ({@link OtherMethod})</li>
 *     <li>{@link ServerCall.Listener#onHalfClose()} ({@link OtherMethod})</li>
 *     <li>{@link ServerCall.Listener#onCancel()} ({@link OnCancel})</li>
 *     <li>{@link ServerCall.Listener#onComplete()} ({@link OnComplete})</li>
 * </ul>
 */
public abstract class ServerCallListenerInstrumentation extends BaseInstrumentation {

    /**
     * Overridden in {@link DynamicTransformer#ensureInstrumented(Class, Collection)},
     * based on the type of the {@linkplain ServerCall.Listener} implementation class.
     */
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return any();
    }

    /**
     * Instruments implementations of {@link ServerCall.Listener}
     * <ul>
     *     <li>{@link ServerCall.Listener#onReady()}()}</li>
     *     <li>{@link ServerCall.Listener#onMessage(Object)}</li>
     *     <li>{@link ServerCall.Listener#onHalfClose()}</li>
     * </ul>
     */
    public static class OtherMethod extends ServerCallListenerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("onReady")
                .or(named("onMessage"))
                .or(named("onHalfClose"));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.grpc.ServerCallListenerInstrumentation$OtherMethod$OtherMethodAdvice";
        }

        public static class OtherMethodAdvice {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object onEnter(@Advice.This ServerCall.Listener<?> listener) {
                return GrpcHelper.getInstance().enterServerListenerMethod(listener);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                                      @Advice.This ServerCall.Listener<?> listener,
                                      @Advice.Enter @Nullable Object transaction) {

                if (transaction instanceof Transaction<?>) {
                    GrpcHelper.getInstance().exitServerListenerMethod(thrown, listener, (Transaction<?>) transaction, null);
                }
            }
        }
    }

    /**
     * Instruments {@link ServerCall.Listener#onCancel()}
     */
    public static class OnCancel extends ServerCallListenerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("onCancel");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.grpc.ServerCallListenerInstrumentation$OnCancel$OnCancelAdvice";
        }

        public static class OnCancelAdvice {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object onEnter(@Advice.This ServerCall.Listener<?> listener) {
                return GrpcHelper.getInstance().enterServerListenerMethod(listener);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                                      @Advice.This ServerCall.Listener<?> listener,
                                      @Advice.Enter @Nullable Object transaction) {

                if (transaction instanceof Transaction<?>) {
                    GrpcHelper.getInstance().exitServerListenerMethod(thrown, listener, (Transaction<?>) transaction, Status.CANCELLED);
                }
            }
        }
    }

    /**
     * Instruments {@link ServerCall.Listener#onComplete()}
     */
    public static class OnComplete extends ServerCallListenerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("onComplete");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.grpc.ServerCallListenerInstrumentation$OnComplete$OnCompleteAdvice";
        }

        public static class OnCompleteAdvice {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object onEnter(@Advice.This ServerCall.Listener<?> listener) {
                return GrpcHelper.getInstance().enterServerListenerMethod(listener);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                                      @Advice.This ServerCall.Listener<?> listener,
                                      @Advice.Enter @Nullable Object transaction) {

                if (transaction instanceof Transaction<?>) {
                    GrpcHelper.getInstance().exitServerListenerMethod(thrown, listener, (Transaction<?>) transaction, Status.OK);
                }
            }
        }
    }
}
