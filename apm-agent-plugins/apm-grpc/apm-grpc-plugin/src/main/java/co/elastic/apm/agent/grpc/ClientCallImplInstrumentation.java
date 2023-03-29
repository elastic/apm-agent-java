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
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments gRPC client calls by relying on {@link ClientCall} internal implementation {@code io.grpc.internal.ClientCallImpl},
 * which provides the full client call lifecycle in a single object instance.
 * <br>
 * full call lifecycle is split in few sub-implementations:
 * <ul>
 *     <li>{@link Start} for client span start</li>
 *     <li>{@link ClientCallListenerInstrumentation.Close} for {@link ClientCall.Listener#onClose} for client span end</li>
 *     <li>{@link ClientCallListenerInstrumentation.OtherListenerMethod} for other methods of {@link ClientCall.Listener}.
 * </ul>
 */
public abstract class ClientCallImplInstrumentation extends BaseInstrumentation {

    private static final Collection<Class<? extends ElasticApmInstrumentation>> RESPONSE_LISTENER_INSTRUMENTATIONS =
        Arrays.<Class<? extends ElasticApmInstrumentation>>asList(
            ClientCallListenerInstrumentation.Close.class,
            ClientCallListenerInstrumentation.OtherListenerMethod.class
        );

    /**
     * Overridden in {@link DynamicTransformer#ensureInstrumented(Class, Collection)},
     * based on the type of the {@linkplain ClientCall} implementation class.
     */
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return any();
    }

    /**
     * Instruments {@link ClientCall#start} to start client call span
     */
    public static class Start extends ClientCallImplInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("start");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.grpc.ClientCallImplInstrumentation$Start$StartAdvice";
        }

        public static class StartAdvice {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object onEnter(@Advice.This ClientCall<?, ?> clientCall,
                                         @Advice.Argument(0) ClientCall.Listener<?> listener,
                                         @Advice.Argument(1) Metadata headers) {

                DynamicTransformer.ensureInstrumented(listener.getClass(), RESPONSE_LISTENER_INSTRUMENTATIONS);
                return GrpcHelper.getInstance().clientCallStartEnter(clientCall, listener, headers);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Advice.Argument(0) ClientCall.Listener<?> listener,
                                      @Advice.Thrown @Nullable Throwable thrown,
                                      @Advice.Enter @Nullable Object span) {

                GrpcHelper.getInstance().clientCallStartExit((Span<?>) span, listener, thrown);
            }
        }
    }

    /**
     * Instruments {@link ClientCall#cancel} to end client call span upon cancellation
     */
    public static class Cancel extends ClientCallImplInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("cancel").and(takesArgument(1, Throwable.class));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.grpc.ClientCallImplInstrumentation$Cancel$CancelAdvice";
        }

        public static class CancelAdvice {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void onEnter(@Advice.This ClientCall<?, ?> clientCall,
                                         @Advice.Argument(1) @Nullable Throwable cause) {

                GrpcHelper.getInstance().cancelCall(clientCall, cause);
            }
        }
    }
}
