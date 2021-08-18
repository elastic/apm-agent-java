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

import co.elastic.apm.agent.sdk.DynamicTransformer;
import io.grpc.Metadata;
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
 * Instruments {@link ServerCall#close(Status, Metadata)} for successful server call execution.
 * Runtime exceptions during call execution are handled with {@link ServerCallListenerInstrumentation}
 */
public class ServerCallInstrumentation extends BaseInstrumentation {

    /**
     * Overridden in {@link DynamicTransformer#ensureInstrumented(Class, Collection)},
     * based on the type of the {@linkplain ServerCall} implementation class.
     */
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return any();
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("close");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.grpc.ServerCallInstrumentation$ServerCallAdvice";
    }

    public static class ServerCallAdvice {

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                                  @Advice.This ServerCall<?, ?> serverCall,
                                  @Advice.Argument(0) Status status) {

            GrpcHelper.getInstance().exitServerCall(status, thrown, serverCall);
        }
    }
}
