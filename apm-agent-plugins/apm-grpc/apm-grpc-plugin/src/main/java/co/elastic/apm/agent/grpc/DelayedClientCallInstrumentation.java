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

import io.grpc.ClientCall;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@code io.grpc.internal.DelayedClientCall#setRealCall(ClientCall)} (introduced in 1.32) to replace the
 * placeholder {@code DelayedClientCall} instance with the real client call.
 * {@code setRealCall()} was chosen over {@code setCall()} because it ensures atomicity.
 * We need to do it this way because prior to 1.35, the creation of the real client call instance could be nested
 * within the creation of the {@code DelayedClientCall}, in which case a single client span would have been created
 * for both and we need to replace the mapped key. In later versions, the real and delayed client call instances are
 * created on different stacks, leading to a span per client call, in which case we need to discard the one
 * corresponding the delayed call.
 */
public class DelayedClientCallInstrumentation extends BaseInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.grpc.internal.DelayedClientCall");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("setRealCall").and(takesArgument(0, named("io.grpc.ClientCall")));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.grpc.DelayedClientCallInstrumentation$DelayedClientCallAdvice";
    }

    public static class DelayedClientCallAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.This ClientCall<?, ?> placeholderClientCall,
                                   @Advice.Argument(0) ClientCall<?, ?> realClientCall) {

            GrpcHelper.getInstance().replaceClientCallRegistration(placeholderClientCall, realClientCall);
        }
    }
}
