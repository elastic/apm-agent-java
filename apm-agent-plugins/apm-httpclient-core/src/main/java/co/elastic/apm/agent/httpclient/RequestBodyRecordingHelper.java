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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.SpanEndListener;
import co.elastic.apm.agent.tracer.metadata.BodyCapture;

public class RequestBodyRecordingHelper implements SpanEndListener<Span<?>> {

    /**
     * We do not need to participate in span reference counting here.
     * Instead, we only hold a reference to the span for the time it is not ended.
     * This is ensured via the {@link #onEnd(Span)} callback.
     */
    // Visible for testing
    Span<?> clientSpan;

    public RequestBodyRecordingHelper(Span<?> clientSpan) {
        if (!clientSpan.isFinished()) {
            this.clientSpan = clientSpan;
            clientSpan.addEndListener(this);
        }
    }


    /**
     * @param b the byte to append
     * @return false, if the body buffer is full and future calls would be no-op. True otherwise.
     */
    public boolean appendToBody(byte b) {
        if (clientSpan != null) {
            BodyCapture requestBody = clientSpan.getContext().getHttp().getRequestBody();
            requestBody.append(b);
            if (requestBody.isFull()) {
                releaseSpan();
            } else {
                return true;
            }
        }
        return false;
    }

    public void appendToBody(byte[] b, int off, int len) {
        if (clientSpan != null) {
            BodyCapture requestBody = clientSpan.getContext().getHttp().getRequestBody();
            requestBody.append(b, off, len);
            if (requestBody.isFull()) {
                releaseSpan();
            }
        }
    }

    void releaseSpan() {
        if (clientSpan != null) {
            clientSpan.removeEndListener(this);
        }
        clientSpan = null;
    }

    @Override
    public void onEnd(Span<?> span) {
        releaseSpan();
    }
}
