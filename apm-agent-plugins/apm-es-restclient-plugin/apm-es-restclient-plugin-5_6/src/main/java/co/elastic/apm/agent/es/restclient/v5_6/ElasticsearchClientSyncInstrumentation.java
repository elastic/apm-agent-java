/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.es.restclient.v5_6;

import co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentation;
import co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentationHelper;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpEntity;
import org.elasticsearch.client.Response;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ElasticsearchClientSyncInstrumentation extends ElasticsearchRestClientInstrumentation {

    @Override
    public Class<?> getAdviceClass() {
        return ElasticsearchRestClientAdvice.class;
    }

    public static class ElasticsearchRestClientAdvice {
        public static ElasticsearchRestClientInstrumentationHelper helper = new ElasticsearchRestClientInstrumentationHelper();

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(@Advice.Argument(0) String method,
                                             @Advice.Argument(1) String endpoint,
                                             @Advice.Argument(3) @Nullable HttpEntity entity) {
            return helper.createClientSpan(tracer.getActive(), method, endpoint, entity);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Return @Nullable Response response,
                                          @Advice.Enter @Nullable Object objSpan,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (objSpan instanceof Span) {
                Span span = (Span) objSpan;
                try {
                    helper.finishClientSpan(response, span, t);
                } finally {
                    span.deactivate();
                }
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.elasticsearch.client.RestClient").
            and(not(
                declaresMethod(named("performRequest")
                    .and(takesArguments(1)
                        .and(takesArgument(0, named("org.elasticsearch.client.Request")))))));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("performRequest")
            .and(takesArguments(6)
                .and(takesArgument(4, named("org.elasticsearch.client.HttpAsyncResponseConsumerFactory"))));
    }
}
