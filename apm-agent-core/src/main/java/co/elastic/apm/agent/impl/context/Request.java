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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.Resetter;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.report.serialize.SerializationConstants;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import javax.annotation.Nullable;
import java.nio.Buffer;
import java.nio.CharBuffer;
import java.util.Enumeration;

import static co.elastic.apm.agent.impl.context.AbstractContext.REDACTED_CONTEXT_STRING;

public class Request implements Recyclable, co.elastic.apm.agent.tracer.metadata.Request {


    private static final ObjectPool<CharBuffer> charBufferPool = QueueBasedObjectPool.of(new MpmcAtomicArrayQueue<CharBuffer>(128), false,
        new Allocator<CharBuffer>() {
            @Override
            public CharBuffer createInstance() {
                return CharBuffer.allocate(SerializationConstants.getMaxLongStringValueLength());
            }
        },
        new Resetter<CharBuffer>() {
            @Override
            public void recycle(CharBuffer object) {
                ((Buffer) object).clear();
            }
        });

    private final PotentiallyMultiValuedMap postParams = new PotentiallyMultiValuedMap();
    /**
     * Should include any headers sent by the requester. Map<String, String> </String,>will be taken by headers if supplied.
     */
    private final PotentiallyMultiValuedMap headers = new PotentiallyMultiValuedMap();
    private final Socket socket = new Socket();
    /**
     * A complete Url, with scheme, host and path.
     * (Required)
     */
    private final Url url = new Url();
    /**
     * A parsed key-value object of cookies
     */
    private final PotentiallyMultiValuedMap cookies = new PotentiallyMultiValuedMap();
    /**
     * Data should only contain the request body (not the query string). It can either be a dictionary (for standard HTTP requests) or a raw request body.
     */
    @Nullable
    private String rawBody;
    /**
     * HTTP version.
     */
    @Nullable
    private String httpVersion;
    /**
     * HTTP method.
     * (Required)
     */
    @Nullable
    private String method;
    @Nullable
    private CharBuffer bodyBuffer;
    private boolean bodyBufferFinished = false;

    /**
     * Data should only contain the request body (not the query string). It can either be a dictionary (for standard HTTP requests) or a raw request body.
     */
    @Nullable
    public Object getBody() {
        if (!postParams.isEmpty()) {
            return postParams;
        } else if (rawBody != null) {
            return rawBody;
        } else {
            return bodyBuffer;
        }
    }

    @Nullable
    public String getRawBody() {
        return rawBody;
    }

    @Override
    public void setRawBody(String rawBody) {
        postParams.resetState();
        if (bodyBuffer != null) {
            charBufferPool.recycle(bodyBuffer);
            bodyBuffer = null;
        }
        this.rawBody = rawBody;
    }

    @Override
    public void redactBody() {
        setRawBody(REDACTED_CONTEXT_STRING);
    }

    public Request addFormUrlEncodedParameter(String key, String value) {
        this.postParams.add(key, value);
        return this;
    }

    @Override
    public Request addFormUrlEncodedParameters(String key, String[] values) {
        this.postParams.set(key, values);
        return this;
    }

    @Override
    public CharBuffer withBodyBuffer() {
        if (this.bodyBuffer == null) {
            this.bodyBuffer = charBufferPool.createInstance();
        }
        return this.bodyBuffer;
    }

    @Override
    public void endOfBufferInput() {
        if (bodyBuffer != null && !bodyBufferFinished) {
            bodyBufferFinished = true;
            ((Buffer) bodyBuffer).flip();
        }
    }

    @Override
    @Nullable
    public CharBuffer getBodyBuffer() {
        if (!bodyBufferFinished) {
            return bodyBuffer;
        } else {
            return null;
        }
    }

    /**
     * Returns the body buffer if it was written to and writing to it was finished through {@link Request#endOfBufferInput()}
     *
     * @return body buffer if it was written to and writing was finished; returns {@code null} otherwise.
     */
    @Nullable
    public CharSequence getBodyBufferForSerialization() {
        if (bodyBufferFinished) {
            return bodyBuffer;
        } else {
            return null;
        }
    }

    public PotentiallyMultiValuedMap getFormUrlEncodedParameters() {
        return postParams;
    }

    /**
     * Adds a request header.
     *
     * @param headerName  The name of the header.
     * @param headerValue The value of the header.
     * @return {@code this}, for fluent method chaining
     */
    public Request addHeader(String headerName, @Nullable String headerValue) {
        if (headerValue != null) {
            headers.add(headerName, headerValue);
        }
        return this;
    }

    @Override
    public Request addHeader(String headerName, @Nullable Enumeration<String> headerValues) {
        if (headerValues != null) {
            while (headerValues.hasMoreElements()) {
                headers.add(headerName, headerValues.nextElement());
            }
        }
        return this;
    }

    @Override
    public PotentiallyMultiValuedMap getHeaders() {
        return headers;
    }

    /**
     * HTTP version.
     */
    @Nullable
    public String getHttpVersion() {
        return httpVersion;
    }

    @Override
    public Request withHttpVersion(@Nullable String httpVersion) {
        if (httpVersion != null) {
            this.httpVersion = getHttpVersion(httpVersion);
        }
        return this;
    }

    private String getHttpVersion(String protocol) {
        // don't allocate new strings in the common cases
        switch (protocol) {
            case "HTTP/1.0":
                return "1.0";
            case "HTTP/1.1":
                return "1.1";
            case "HTTP/2.0":
                return "2.0";
            default:
                return protocol.replace("HTTP/", "");
        }
    }

    /**
     * HTTP method.
     * (Required)
     */
    @Nullable
    public String getMethod() {
        return method;
    }

    @Override
    public Request withMethod(@Nullable String method) {
        this.method = method;
        return this;
    }

    @Override
    public Socket getSocket() {
        return socket;
    }

    @Override
    public Url getUrl() {
        return url;
    }

    @Override
    public Request addCookie(String cookieName, String cookieValue) {
        cookies.add(cookieName, cookieValue);
        return this;
    }

    public PotentiallyMultiValuedMap getCookies() {
        return cookies;
    }

    void onTransactionEnd() {
        endOfBufferInput();
    }

    @Override
    public void resetState() {
        postParams.resetState();
        headers.resetState();
        httpVersion = null;
        method = null;
        socket.resetState();
        url.resetState();
        cookies.resetState();
        bodyBufferFinished = false;
        if (bodyBuffer != null) {
            charBufferPool.recycle(bodyBuffer);
            bodyBuffer = null;
        }
        rawBody = null;
    }

    public void copyFrom(Request other) {
        this.postParams.copyFrom(other.postParams);
        this.headers.copyFrom(other.headers);
        this.httpVersion = other.httpVersion;
        this.method = other.method;
        this.socket.copyFrom(other.socket);
        this.url.copyFrom(other.url);
        this.cookies.copyFrom(other.cookies);
        // Using getBodyBufferForSerialization to make sure we copy body buffer only if it was written to and writing was finished
        final CharSequence otherBuffer = other.getBodyBufferForSerialization();
        if (otherBuffer != null) {
            final CharBuffer thisBuffer = this.withBodyBuffer();
            for (int i = 0; i < otherBuffer.length(); i++) {
                thisBuffer.append(otherBuffer.charAt(i));
            }
            endOfBufferInput();
        }
        this.rawBody = other.rawBody;
    }

    @Override
    public boolean hasContent() {
        return method != null ||
            headers.size() > 0 ||
            httpVersion != null ||
            cookies.size() > 0 ||
            postParams.size() > 0 ||
            socket.hasContent() ||
            url.hasContent();
    }
}
