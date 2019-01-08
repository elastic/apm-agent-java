/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
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

import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;

import javax.annotation.Nullable;
import java.util.Enumeration;


/**
 * Request
 * <p>
 * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
 */
public class Request implements Recyclable {

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

    /**
     * Data should only contain the request body (not the query string). It can either be a dictionary (for standard HTTP requests) or a raw request body.
     */
    @Nullable
    public Object getBody() {
        if (!postParams.isEmpty()) {
            return postParams;
        } else {
            return rawBody;
        }
    }

    @Nullable
    public String getRawBody() {
        return rawBody;
    }

    public void redactBody() {
        postParams.resetState();
        rawBody = "[REDACTED]";
    }

    public Request addFormUrlEncodedParameter(String key, String value) {
        this.postParams.add(key, value);
        return this;
    }

    public Request addFormUrlEncodedParameters(String key, String[] values) {
        this.postParams.set(key, values);
        return this;
    }

    public Request withRawBody(String rawBody) {
        this.rawBody = rawBody;
        return this;
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

    @Override
    public void resetState() {
        rawBody = null;
        postParams.resetState();
        headers.resetState();
        httpVersion = null;
        method = null;
        socket.resetState();
        url.resetState();
        cookies.resetState();
    }

    public void copyFrom(Request other) {
        this.rawBody = other.rawBody;
        this.postParams.copyFrom(other.postParams);
        this.headers.copyFrom(other.headers);
        this.httpVersion = other.httpVersion;
        this.method = other.method;
        this.socket.copyFrom(other.socket);
        this.url.copyFrom(other.url);
        this.cookies.copyFrom(other.cookies);
    }

    public boolean hasContent() {
        return method != null ||
            headers.size() > 0 ||
            httpVersion != null ||
            cookies.size() > 0 ||
            rawBody != null ||
            postParams.size() > 0 ||
            socket.hasContent() ||
            url.hasContent();
    }
}
