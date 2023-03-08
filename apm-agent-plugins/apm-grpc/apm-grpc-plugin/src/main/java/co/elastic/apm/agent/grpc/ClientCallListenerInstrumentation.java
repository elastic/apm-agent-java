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

import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.sdk.DynamicTransformer;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class ClientCallListenerInstrumentation extends BaseInstrumentation {

    /**
     * Overridden in {@link DynamicTransformer#ensureInstrumented(Class, Collection)},
     * based on the type of the {@linkplain ClientCall.Listener} implementation class.
     */
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return any();
    }

    /**
     * Instruments {@link ClientCall.Listener#onClose(Status, Metadata)} )} to capture any thrown exception and end current span
     */
    public static class Close extends ClientCallListenerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("onClose");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.grpc.ClientCallListenerInstrumentation$Close$CloseAdvice";
        }

        public static class CloseAdvice {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object onEnter(@Advice.This ClientCall.Listener<?> listener) {
                return GrpcHelper.getInstance().enterClientListenerMethod(listener);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                      @Advice.Argument(0) Status status,
                                      @Advice.Thrown @Nullable Throwable thrown,
                                      @Advice.Enter @Nullable Object span) {

                GrpcHelper.getInstance().exitClientListenerMethod(thrown, listener, (Span<?>) span, status);
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
    public static class OtherListenerMethod extends ClientCallListenerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("onMessage")
                .or(named("onHeaders"))
                .or(named("onReady"));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.grpc.ClientCallListenerInstrumentation$OtherListenerMethod$OtherMethodAdvice";
        }

        public static class OtherMethodAdvice {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object onEnter(@Advice.This ClientCall.Listener<?> listener) {
                return GrpcHelper.getInstance().enterClientListenerMethod(listener);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Advice.This ClientCall.Listener<?> listener,
                                      @Advice.Thrown @Nullable Throwable thrown,
                                      @Advice.Enter @Nullable Object span) {

                GrpcHelper.getInstance().exitClientListenerMethod(thrown, listener, (Span<?>) span, null);
            }
        }
    }
}
