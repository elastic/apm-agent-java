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
package co.elastic.apm.agent.springwebclient;

import co.elastic.apm.agent.httpclient.RequestBodyRecordingHelper;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import org.springframework.http.client.reactive.ClientHttpRequest;

import javax.annotation.Nullable;

public class BodyCaptureRegistry {

    private static final WeakMap<ClientHttpRequest, RequestBodyRecordingHelper> PENDING_RECORDINGS = WeakConcurrent.buildMap();

    public static void maybeCaptureBodyFor(AbstractSpan<?> abstractSpan, ClientHttpRequest request) {
        if (!(abstractSpan instanceof Span<?>)) {
            return;
        }
        Span<?> span = (Span<?>) abstractSpan;
        if (span.getContext().getHttp().getRequestBody().startCapture()) {
            PENDING_RECORDINGS.put(request, new RequestBodyRecordingHelper(span));
        }
    }

    @Nullable
    public static RequestBodyRecordingHelper activateRecording(ClientHttpRequest request) {
        return PENDING_RECORDINGS.remove(request);
    }
}
