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
package co.elastic.apm.agent.tracer.metadata;

import javax.annotation.Nullable;
import java.nio.CharBuffer;
import java.util.Enumeration;

/**
 * Request
 * <p>
 * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
 */
public interface Request {

    boolean hasContent();

    Request withMethod(@Nullable String method);

    Socket getSocket();

    /**
     * A complete Url, with scheme, host and path.
     * (Required)
     */
    Url getUrl();

    /**
     * Should include any headers sent by the requester.
     */
    PotentiallyMultiValuedMap getHeaders();

    /**
     * A parsed key-value object of cookies
     */
    PotentiallyMultiValuedMap getCookies();

    Request withHttpVersion(@Nullable String httpVersion);

    /**
     * Gets a pooled {@link CharBuffer} to record the request body and associates it with this instance.
     * <p>
     * Note: you may not hold a reference to the returned {@link CharBuffer} as it will be reused.
     * </p>
     * <p>
     * Note: this method is not thread safe
     * </p>
     * <p>
     * Note: In order for the value written to the body buffer to be used, you must call {@link Request#endOfBufferInput()},
     * which is the only valid way to invoke {@link CharBuffer#flip()} on the body buffer.
     * </p>
     *
     * @return a {@link CharBuffer} to record the request body
     */
    @Nullable
    CharBuffer withBodyBuffer();

    void redactBody();

    Request addFormUrlEncodedParameters(String key, String[] values);

    Request addHeader(String headerName, @Nullable Enumeration<String> headerValues);

    Request addCookie(String cookieName, String cookieValue);

    /**
     * Returns the associated pooled {@link CharBuffer} to record the request body.
     * <p>
     * Note: returns {@code null} unless {@link #withBodyBuffer()} has previously been called
     * </p>
     *
     * @return a {@link CharBuffer} to record the request body, or {@code null}
     */
    @Nullable
    CharBuffer getBodyBuffer();

    void endOfBufferInput();

    /**
     * Sets the body as a raw string and removes any previously set parameter or body buffer.
     *
     * @param rawBody the body as a raw string
     */
    void setRawBody(String rawBody);
}
