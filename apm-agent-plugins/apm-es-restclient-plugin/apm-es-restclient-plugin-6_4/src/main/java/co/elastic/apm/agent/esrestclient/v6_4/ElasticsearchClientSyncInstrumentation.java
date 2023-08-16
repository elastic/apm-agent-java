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
package co.elastic.apm.agent.esrestclient.v6_4;

import co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentation;
import co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentationHelper;
import co.elastic.apm.agent.tracer.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ElasticsearchClientSyncInstrumentation extends ElasticsearchRestClientInstrumentation {

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$ElasticsearchRestClientSyncAdvice";
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.elasticsearch.client.RestClient");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("performRequest")
            .and(takesArguments(1)
                .and(takesArgument(0, named("org.elasticsearch.client.Request"))));
    }

    public static class ElasticsearchRestClientSyncAdvice {

        private static final ElasticsearchRestClientInstrumentationHelper helper = ElasticsearchRestClientInstrumentationHelper.get();

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(@Advice.Argument(0) Request request) {
            return helper.createClientSpan(request.getMethod(), request.getEndpoint(), request.getEntity());
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Return @Nullable Response response,
                                          @Advice.Enter @Nullable Object spanObj,
                                          @Advice.Thrown @Nullable Throwable t) {
            Span<?> span = (Span<?>) spanObj;
            if (span != null) {
                try {
                    helper.finishClientSpan(response, span, t);
                } finally {
                    span.deactivate();
                }
            }
        }
    }
}
