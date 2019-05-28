/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;

public class Http implements Recyclable {

    /**
     * URL used by this HTTP outgoing span
     */
    @Nullable
    private String url;

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
    @Nullable
    public String getUrl() {
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
            this.url = sanitize(url);
        }
        return this;
    }

    @Nullable
    private static String sanitize(String uriString) {
        if (uriString.indexOf('@') < 0) {
            return uriString;
        }
        final URI uri;
        try {
            uri = new URI(uriString);
            if (uri.getUserInfo() != null) {
                return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
            } else {
                return uri.toString();
            }
        } catch (URISyntaxException e) {
            return null;
        }
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
        url = null;
        method = null;
        statusCode = 0;
    }

    public boolean hasContent() {
        return url != null ||
            method != null ||
            statusCode > 0;
    }

    public void copyFrom(Http other) {
        url = other.url;
        method = other.method;
        statusCode = other.statusCode;
    }
}
