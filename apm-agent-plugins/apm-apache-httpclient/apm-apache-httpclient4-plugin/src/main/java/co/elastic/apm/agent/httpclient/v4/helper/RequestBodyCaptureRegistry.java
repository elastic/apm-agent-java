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
package co.elastic.apm.agent.httpclient.v4.helper;

import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.reference.ReferenceCountedMap;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;

import javax.annotation.Nullable;

public class RequestBodyCaptureRegistry {

    private static final Logger logger = LoggerFactory.getLogger(RequestBodyCaptureRegistry.class);

    @GlobalState
    public static class MapHolder {
        private static final ReferenceCountedMap<Object, Span<?>> entityToClientSpan = GlobalTracer.get().newReferenceCountedMap();


        public static void captureBodyFor(Object entity, Span<?> httpClientSpan) {
            entityToClientSpan.put(entity, httpClientSpan);
        }

        @Nullable
        public static Span<?> removeSpanFor(Object entity) {
            return entityToClientSpan.remove(entity);
        }
    }


    public static void potentiallyCaptureRequestBody(HttpRequest request, @Nullable AbstractSpan<?> span) {
        if (HttpClientHelper.startRequestBodyCapture(span, request, RequestHeaderAccessor.INSTANCE)) {
            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                if (entity != null) {
                    logger.debug("Enabling request capture for entity {}() for span {}", entity.getClass().getName(), System.identityHashCode(entity), span);
                    MapHolder.captureBodyFor(entity, (Span<?>) span);
                } else {
                    logger.debug("HttpEntity is null for span {}", span);
                }
            } else {
                logger.debug("Not capturing request body because {} is not an HttpEntityEnclosingRequest", request.getClass().getName());
            }
        }
    }

    @Nullable
    public static Span<?> removeSpanFor(HttpEntity entity) {
        return MapHolder.removeSpanFor(entity);
    }

}
