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

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import com.alibaba.dubbo.remoting.exchange.ResponseCallback;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class AlibabaResponseFutureInstrumentation extends AbstractAlibabaDubboInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("com.alibaba.dubbo.remoting.exchange.ResponseFuture"));
    }

    /**
     * {@link com.alibaba.dubbo.remoting.exchange.ResponseFuture#setCallback(ResponseCallback)}
     */
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("setCallback");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.dubbo.AlibabaResponseFutureInstrumentation$AlibabaResponseFutureAdvice";
    }

    public static class AlibabaResponseFutureAdvice {

        private static final List<Class<? extends ElasticApmInstrumentation>> RESPONSE_CALLBACK_INSTRUMENTATIONS = Arrays.<Class<? extends ElasticApmInstrumentation>>asList(
            AlibabaResponseCallbackInstrumentation.CaughtInstrumentation.class,
            AlibabaResponseCallbackInstrumentation.DoneInstrumentation.class);

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.Argument(value = 0) ResponseCallback callback) {
            AbstractSpan<?> active = tracer.getActive();
            if (active == null) {
                return;
            }
            AlibabaCallbackHolder.callbackSpanMap.put(callback, active);
            DynamicTransformer.ensureInstrumented(callback.getClass(), RESPONSE_CALLBACK_INSTRUMENTATIONS);
        }
    }

}
