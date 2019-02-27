/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.objectpool.impl.Resetter;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import javax.annotation.Nullable;
import java.nio.Buffer;
import java.nio.CharBuffer;
import java.util.Enumeration;


/**
 * Request
 * <p>
 * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
 */
public class Request implements Recyclable {


    private static final ObjectPool<CharBuffer> charBufferPool = QueueBasedObjectPool.of(new MpmcAtomicArrayQueue<CharBuffer>(128), false,
        new Allocator<CharBuffer>() {
            @Override
            public CharBuffer createInstance() {
                return CharBuffer.allocate(DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH);
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

    public void setRawBody(String rawBody) {
        postParams.resetState();
        if (bodyBuffer != null) {
            charBufferPool.recycle(bodyBuffer);
            bodyBuffer = null;
        }
        this.rawBody = rawBody;
    }

    public void redactBody() {
        setRawBody("[REDACTED]");
    }

    public Request addFormUrlEncodedParameter(String key, String value) {
        this.postParams.add(key, value);
        return this;
    }

    public Request addFormUrlEncodedParameters(String key, String[] values) {
        this.postParams.set(key, values);
        return this;
    }

    /**
     * Gets a pooled {@link CharBuffer} to record the request body and associates it with this instance.
     * <p>
     * Note: you may not hold a reference to the returned {@link CharBuffer} as it will be reused.
     * </p>
     * <p>
     * Note: This method is not thread safe
     * </p>
     *
     * @return a {@link CharBuffer} to record the request body
     */
    public CharBuffer withBodyBuffer() {
        if (this.bodyBuffer == null) {
            this.bodyBuffer = charBufferPool.createInstance();
        }
        return this.bodyBuffer;
    }

    public void endOfBufferInput() {
        if (bodyBuffer != null && !bodyBufferFinished) {
            bodyBufferFinished = true;
            ((Buffer) bodyBuffer).flip();
        }
    }

    /**
     * Returns the associated pooled {@link CharBuffer} to record the request body.
     * <p>
     * Note: returns {@code null} unless {@link #withBodyBuffer()} has previously been called
     * </p>
     *
     * @return a {@link CharBuffer} to record the request body, or {@code null}
     */
    @Nullable
    public CharBuffer getBodyBuffer() {
        if (!bodyBufferFinished) {
            return bodyBuffer;
        } else {
            return null;
        }
    }

    @Nullable
    public CharBuffer getBodyBufferForSerialization() {
        return bodyBuffer;
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

    public Request addHeader(String headerName, @Nullable Enumeration<String> headerValues) {
        if (headerValues != null) {
            while (headerValues.hasMoreElements()) {
                headers.add(headerName, headerValues.nextElement());
            }
        }
        return this;
    }

    /**
     * Should include any headers sent by the requester.
     */
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

    public Request withHttpVersion(@Nullable String httpVersion) {
        this.httpVersion = httpVersion;
        return this;
    }

    /**
     * HTTP method.
     * (Required)
     */
    @Nullable
    public String getMethod() {
        return method;
    }

    public Request withMethod(@Nullable String method) {
        this.method = method;
        return this;
    }

    public Socket getSocket() {
        return socket;
    }

    /**
     * A complete Url, with scheme, host and path.
     * (Required)
     */
    public Url getUrl() {
        return url;
    }


    public Request addCookie(String cookieName, String cookieValue) {
        cookies.add(cookieName, cookieValue);
        return this;
    }

    /**
     * A parsed key-value object of cookies
     */
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
        if (bodyBuffer != null) {
            charBufferPool.recycle(bodyBuffer);
            bodyBuffer = null;
        }
    }

    public void copyFrom(Request other) {
        this.postParams.copyFrom(other.postParams);
        this.headers.copyFrom(other.headers);
        this.httpVersion = other.httpVersion;
        this.method = other.method;
        this.socket.copyFrom(other.socket);
        this.url.copyFrom(other.url);
        this.cookies.copyFrom(other.cookies);
        if (other.bodyBuffer != null) {
            final CharBuffer otherBuffer = other.bodyBuffer;
            final CharBuffer thisBuffer = this.withBodyBuffer();
            for (int i = 0; i < otherBuffer.length(); i++) {
                thisBuffer.append(otherBuffer.charAt(i));
            }
            ((Buffer) thisBuffer).flip();
        }
    }

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
