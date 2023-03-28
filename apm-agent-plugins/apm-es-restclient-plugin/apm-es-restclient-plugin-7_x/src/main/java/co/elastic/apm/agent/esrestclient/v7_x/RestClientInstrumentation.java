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
package co.elastic.apm.agent.esrestclient.v7_x;

import co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentation;
import co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentationHelper;
import co.elastic.apm.agent.tracer.AbstractSpan;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.client.ResponseListener;

import javax.annotation.Nullable;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Instruments {@link org.elasticsearch.client.RestClient#performRequestAsync(org.elasticsearch.client.Request, org.elasticsearch.client.ResponseListener)}
 */
public class RestClientInstrumentation extends ElasticsearchRestClientInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.elasticsearch.client.RestClient");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("performRequestAsync").and(isPublic())
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.elasticsearch.client.Request")))
            .and(takesArgument(1, named("org.elasticsearch.client.ResponseListener")))
            .and(returns(named("org.elasticsearch.client.Cancellable")));
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$RestClientAsyncAdvice";
    }

    public static class RestClientAsyncAdvice {

        private static final ElasticsearchRestClientInstrumentationHelper helper = ElasticsearchRestClientInstrumentationHelper.get();

        @Nullable
        @Advice.AssignReturned.ToArguments(@ToArgument(value = 1, typing = DYNAMIC))
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static ResponseListener onEnter(@Advice.Argument(1) @Nullable ResponseListener listenerArg) {

            AbstractSpan<?> activeContext = tracer.getActive();

            ResponseListener listener =  listenerArg;
            if (listener != null && activeContext != null) {
                listener = helper.wrapContextPropagationContextListener(listener, activeContext);
            }

            return listener;
        }

    }
}
