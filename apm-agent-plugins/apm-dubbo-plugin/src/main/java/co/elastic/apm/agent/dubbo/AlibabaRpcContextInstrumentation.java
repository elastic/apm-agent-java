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
package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.protocol.dubbo.FutureAdapter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class AlibabaRpcContextInstrumentation extends AbstractAlibabaDubboInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.alibaba.dubbo.rpc.RpcContext");
    }

    /**
     * {@link RpcContext#setFuture(java.util.concurrent.Future)}
     */
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("setFuture").and(takesArgument(0, named("java.util.concurrent.Future")));
    }

    public static class AdviceClass {
        private static final List<Class<? extends ElasticApmInstrumentation>> RESPONSE_FUTURE_INSTRUMENTATION =
            Collections.<Class<? extends ElasticApmInstrumentation>>singletonList(AlibabaResponseFutureInstrumentation.class);
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.Argument(0) Future<?> future) {
            if (future instanceof FutureAdapter) {
                DynamicTransformer.ensureInstrumented(((FutureAdapter<?>) future).getFuture().getClass(), RESPONSE_FUTURE_INSTRUMENTATION);
            }
        }
    }
}
