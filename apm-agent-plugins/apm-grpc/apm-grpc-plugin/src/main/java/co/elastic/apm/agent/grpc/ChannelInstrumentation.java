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
import io.grpc.CallOptions;
import io.grpc.Channel;
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

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Instruments {@link Channel#newCall(MethodDescriptor, CallOptions)} to add channel authority (host+port) to the span
 * linked to the returned client call instance (if any).
 */
public class ChannelInstrumentation extends BaseInstrumentation {

    private static final Collection<Class<? extends ElasticApmInstrumentation>> CLIENT_CALL_INSTRUMENTATION =
        Arrays.<Class<? extends ElasticApmInstrumentation>>asList(
            ClientCallImplInstrumentation.Start.class,
            ClientCallImplInstrumentation.Cancel.class
        );

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("io.grpc").and(nameContains("Channel"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("io.grpc.Channel")
            .and(not(named("io.grpc.internal.ForwardingManagedChannel"))));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("newCall");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.grpc.ChannelInstrumentation$ChannelAdvice";
    }

    public static class ChannelAdvice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This Channel channel,
                                     @Advice.Argument(0) MethodDescriptor<?, ?> method) {

            return GrpcHelper.getInstance().onClientCallCreationEntry(tracer.getActive(), method, channel.authority());
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Return @Nullable ClientCall<?, ?> clientCall,
                                  @Advice.Enter @Nullable Object span) {

            if (clientCall != null) {
                DynamicTransformer.ensureInstrumented(clientCall.getClass(), CLIENT_CALL_INSTRUMENTATION);
            }
            GrpcHelper.getInstance().onClientCallCreationExit(clientCall, (Span<?>) span);
        }
    }
}
