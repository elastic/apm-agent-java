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

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;

public class Http implements Recyclable {

    /**
     * URL used by this HTTP outgoing span
     */
    private final Url url = new Url();

    /**
     * HTTP method used by this HTTP outgoing span
     */
    @Nullable
    private String method;

    /**
     * Status code of the response
     */
    private int statusCode;

    /**
     * URL used for the outgoing HTTP call
     */
    public CharSequence getUrl() {
        // note: do not expose the underlying Url object, as it might not have
        // all it's properties set due to providing the full URL as-is
        return url.getFull();
    }

    /**
     * @return internal {@link Url} instance
     */
    public Url getInternalUrl() {
        return url;
    }

    @Nullable
    public String getMethod() {
        return method;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * URL used for the outgoing HTTP call
     */
    public Http withUrl(@Nullable String url) {
        if (url != null) {
            this.url.withFull(url);
        }
        return this;
    }

    public Http withMethod(String method) {
        this.method = method;
        return this;
    }

    public Http withStatusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    @Override
    public void resetState() {
        url.resetState();
        method = null;
        statusCode = 0;
    }

    public boolean hasContent() {
        return url.hasContent() ||
            method != null ||
            statusCode > 0;
    }

    public void copyFrom(Http other) {
        url.copyFrom(other.url);
        method = other.method;
        statusCode = other.statusCode;
    }
}
