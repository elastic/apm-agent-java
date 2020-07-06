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

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentation;
import co.elastic.apm.agent.es.restclient.ElasticsearchRestClientInstrumentationHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import co.elastic.apm.agent.sdk.state.GlobalThreadLocal;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpEntity;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ElasticsearchClientAsyncInstrumentation extends ElasticsearchRestClientInstrumentation {

    public ElasticsearchClientAsyncInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public Class<?> getAdviceClass() {
        return ElasticsearchRestClientAsyncAdvice.class;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.elasticsearch.client.RestClient").
            and(not(
                declaresMethod(named("performRequestAsync")
                    .and(takesArguments(2)
                        .and(takesArgument(0, named("org.elasticsearch.client.Request")))))));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("performRequestAsync")
            .and(takesArguments(7)
                .and(takesArgument(4, named("org.elasticsearch.client.HttpAsyncResponseConsumerFactory")))
                .and(takesArgument(5, named("org.elasticsearch.client.ResponseListener"))));
    }


    public static class ElasticsearchRestClientAsyncAdvice {
        @VisibleForAdvice
        public static final GlobalThreadLocal<Span> spanTls = GlobalThreadLocal.get(ElasticsearchRestClientAsyncAdvice.class, "spanTls");
        @AssignTo.Argument(5)
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static ResponseListener onBeforeExecute(@Advice.Argument(0) String method,
                                                       @Advice.Argument(1) String endpoint,
                                                       @Advice.Argument(3) @Nullable HttpEntity entity,
                                                       @Advice.Argument(5) ResponseListener responseListener) {

            ElasticsearchRestClientInstrumentationHelper<HttpEntity, Response, ResponseListener> helper = esClientInstrHelperManager.getForClassLoaderOfClass(Response.class);
            if (helper != null) {
                Span span = helper.createClientSpan(method, endpoint, entity);
                spanTls.set(span);
                if (span != null) {
                    return helper.<ResponseListener>wrapResponseListener(responseListener, span);
                }
            }
            return responseListener;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Thrown @Nullable Throwable t) {
            final Span span = spanTls.getAndRemove();
            if (span != null) {
                // Deactivate in this thread. Span will be ended and reported by the listener
                span.deactivate();
            }
        }
    }
}
