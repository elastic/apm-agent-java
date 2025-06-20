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
package co.elastic.apm.agent.httpclient.common;


import co.elastic.apm.agent.httpclient.RequestBodyRecordingInputStream;
import co.elastic.apm.agent.httpclient.RequestBodyRecordingOutputStream;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.Span;

import java.io.InputStream;
import java.io.OutputStream;

public abstract class AbstractApacheHttpRequestBodyCaptureAdvice {

    private static final Logger logger = LoggerFactory.getLogger(AbstractApacheHttpRequestBodyCaptureAdvice.class);

    public static <HTTPENTITY> InputStream maybeCaptureRequestBodyInputStream(HTTPENTITY thiz, InputStream requestBody) {
        Span<?> clientSpan = RequestBodyCaptureRegistry.removeSpanFor(thiz);
        if (clientSpan != null) {
            logger.debug("Wrapping input stream for request body capture for HttpEntity {} ({}) for span {}", thiz.getClass().getName(), System.identityHashCode(thiz), clientSpan);
            return new RequestBodyRecordingInputStream(requestBody, clientSpan);
        }
        return requestBody;
    }

    public static <HTTPENTITY> OutputStream maybeCaptureRequestBodyOutputStream(HTTPENTITY thiz, OutputStream requestBody) {
        Span<?> clientSpan = RequestBodyCaptureRegistry.removeSpanFor(thiz);
        if (clientSpan != null) {
            logger.debug("Wrapping output stream for request body capture for HttpEntity {} ({}) for span {}", thiz.getClass().getName(), System.identityHashCode(thiz), clientSpan);
            return new RequestBodyRecordingOutputStream(requestBody, clientSpan);
        }
        return requestBody;
    }

    public static void releaseRequestBodyOutputStream(OutputStream maybeWrapped) {
        if (maybeWrapped instanceof RequestBodyRecordingOutputStream) {
            ((RequestBodyRecordingOutputStream) maybeWrapped).releaseSpan();
        }
    }
}
