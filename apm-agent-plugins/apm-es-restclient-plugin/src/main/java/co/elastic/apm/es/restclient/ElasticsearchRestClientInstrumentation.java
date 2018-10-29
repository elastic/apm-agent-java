/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.es.restclient;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Instrumentation for Elasticsearch RestClient, currently supporting only synchronized queries.
 * All sync operations go through org.elasticsearch.client.RestClient#performRequest(org.elasticsearch.client.Request)
 */
public class ElasticsearchRestClientInstrumentation extends ElasticApmInstrumentation {
    @VisibleForAdvice
    public static final String SEARCH_QUERY_PATH_SUFFIX = "_search";
    @VisibleForAdvice
    public static final String SPAN_TYPE = "db.elasticsearch.request";
    @VisibleForAdvice
    public static final String DB_CONTEXT_TYPE = "elasticsearch";

    @Advice.OnMethodEnter
    private static void onBeforeExecute(@Advice.Argument(0) Request request,
                                        @Advice.Local("span") Span span) {
        if (tracer == null) {
            return;
        }
        final AbstractSpan<?> activeSpan = tracer.getActive();
        if (activeSpan == null || !activeSpan.isSampled()) {
            return;
        }
        span = activeSpan.createSpan()
            .withType(SPAN_TYPE)
            .appendToName("Elasticsearch: ").appendToName(request.getMethod()).appendToName(" ").appendToName(request.getEndpoint());
        span.getContext().getDb().withType(DB_CONTEXT_TYPE);
        span.activate();

        if (span.isSampled()) {
            span.getContext().getHttp().withMethod(request.getMethod());
            if (request.getEndpoint().endsWith(SEARCH_QUERY_PATH_SUFFIX)) {
                HttpEntity entity = request.getEntity();
                if (entity != null && entity.isRepeatable()) {
                    try {
                        String body = ESRestClientInstrumentationHelper.readRequestBody(entity.getContent(), request.getEndpoint());
                        if (body != null && !body.isEmpty()) {
                            span.getContext().getDb().withStatement(body);
                        }
                    } catch (IOException e) {
                        // We can't log from here
                    }
                }
            }
        }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onAfterExecute(@Advice.Return @Nullable Response response,
                                      @Advice.Local("span") @Nullable Span span,
                                      @Advice.Thrown @Nullable Throwable t) {
        if (span != null) {
            try {
                String url = null;
                int statusCode = -1;
                if(response != null) {
                    url = response.getHost().toURI();
                    statusCode = response.getStatusLine().getStatusCode();
                } else if(t != null) {
                    if (t instanceof ResponseException) {
                        ResponseException esre = (ResponseException) t;
                        url = esre.getResponse().getHost().toURI();
                        statusCode = esre.getResponse().getStatusLine().getStatusCode();

                        /*
                        // Add tags so that they will be copied to error capture
                        span.addTag(QUERY_STATUS_CODE_KEY, Integer.toString(statusCode));
                        span.addTag(ELASTICSEARCH_NODE_URL_KEY, url);
                        span.addTag(ERROR_REASON_KEY, esre.getResponse().getStatusLine().getReasonPhrase());
                        */
                    }
                    span.captureException(t);
                }

                if(url != null && !url.isEmpty()) {
                    span.getContext().getHttp().withUrl(url);
                }
                if(statusCode > 0) {
                    span.getContext().getHttp().withStatusCode(statusCode);
                }
            } finally {
                span.deactivate().end();
            }

        }
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

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("elasticsearch-restclient");
    }
}
