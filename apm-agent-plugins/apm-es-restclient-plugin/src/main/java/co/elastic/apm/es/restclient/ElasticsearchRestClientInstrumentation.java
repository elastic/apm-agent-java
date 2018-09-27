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
import co.elastic.apm.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.Header;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Instrumentation for Elasticsearch RestClient, currently supporting only synchronized queries.
 * All sync operations go through org.elasticsearch.client.RestClient#performRequest(org.elasticsearch.client.Request)
 */
public class ElasticsearchRestClientInstrumentation extends ElasticApmInstrumentation {
    private static final String ES_REST_CLIENT_INSTRUMENTATION_GROUP = "elasticsearch-restclient";
    private static final String SPAN_TYPE = "es-restclient";

    private static final String REST_CLIENT_CLASS_NAME = "org.elasticsearch.client.RestClient";
    private static final String REQUEST_CLASS_NAME = "org.elasticsearch.client.Request";
    private static final String PERFORM_REQUEST_METHOD_NAME = "performRequest";

    static final String ELASTICSEARCH_NODE_KEY = "Elasticsearch-node";
    static final String QUERY_STATUS_CODE_KEY = "Query-status-code";
    static final String ERROR_REASON_KEY = "Error-reason";

    @Advice.OnMethodEnter
    private static void onBeforeExecute(@Advice.Argument(0) Request request,
                                        @Advice.Local("span") Span span,
                                        @Advice.Local("esre") ResponseException esre) {
        if (tracer == null || tracer.getActive() == null) {
            return;
        }
        span = tracer.getActive().createSpan()
            .withType(SPAN_TYPE)
            .appendToName(request.getEndpoint()).appendToName(" ").appendToName(request.getMethod())
            .activate();


        // Add request parameters to query info
        for (Map.Entry<String, String> param: request.getParameters().entrySet()) {
            span.addTag(param.getKey(), param.getValue());
        }

        // Add request options to query info
        for (Header header: request.getOptions().getHeaders()) {
            span.addTag(header.getName(), header.getValue());
        }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onAfterExecute(@Advice.Return @Nullable Response response,
                                      @Advice.Local("span") @Nullable Span span,
                                      @Advice.Local("esre") ResponseException esre,
                                      @Advice.Thrown @Nullable Throwable t) {
        if (span != null) {
            if(response != null) {
                span.addTag(ELASTICSEARCH_NODE_KEY, response.getHost().toHostString());
                span.addTag(QUERY_STATUS_CODE_KEY, Integer.toString(response.getStatusLine().getStatusCode()));
            }
            else if(t instanceof ResponseException)
            {
                esre = (ResponseException) t;
                span.addTag(QUERY_STATUS_CODE_KEY, Integer.toString(esre.getResponse().getStatusLine().getStatusCode()));
                span.addTag(ERROR_REASON_KEY, esre.getResponse().getStatusLine().getReasonPhrase());
                span.addTag(ELASTICSEARCH_NODE_KEY, esre.getResponse().getHost().toHostString());
                span.captureException(t);
            }

            span.deactivate().end();
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named(REST_CLIENT_CLASS_NAME);
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named(PERFORM_REQUEST_METHOD_NAME)
            .and(takesArguments(1)
            .and(takesArgument(0, named(REQUEST_CLASS_NAME))));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(ES_REST_CLIENT_INSTRUMENTATION_GROUP);
    }
}
