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
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseListener;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ElasticsearchClientAsyncInstrumentation extends ElasticsearchRestClientInstrumentation {

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$ElasticsearchRestClientAsyncAdvice";
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.elasticsearch.client.RestClient");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("performRequestAsync")
            .and(takesArguments(2)
                .and(takesArgument(0, named("org.elasticsearch.client.Request")))
                .and(takesArgument(1, named("org.elasticsearch.client.ResponseListener"))));
    }

    public static class ElasticsearchRestClientAsyncAdvice {

        @Nullable
        @AssignTo.Argument(index = 1, value = 1)
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object[] onBeforeExecute(@Advice.Argument(0) Request request,
                                               @Advice.Argument(1) ResponseListener responseListener) {
            Span span = helper.createClientSpan(request.getMethod(), request.getEndpoint(), request.getEntity());
            if (span != null) {
                Object[] ret = new Object[2];
                ret[0] = span;
                ret[1] = helper.<ResponseListener>wrapResponseListener(responseListener, span);
                return ret;
            }
            return null;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Thrown @Nullable Throwable t,
                                          @Advice.Enter @Nullable Object[] entryArgs) {
            if (entryArgs != null) {
                final Span span = (Span) entryArgs[0];
                if (span != null) {
                    // Deactivate in this thread. Span will be ended and reported by the listener
                    span.deactivate();
                }
            }
        }
    }
}
