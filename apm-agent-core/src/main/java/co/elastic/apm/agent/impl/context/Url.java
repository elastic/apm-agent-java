/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;


/**
 * A complete Url, with scheme, host and path.
 */
public class Url implements Recyclable {

    /**
     * The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    private final StringBuilder full = new StringBuilder();
    /**
     * The protocol of the request, e.g. 'https:'.
     */
    @Nullable
    private String protocol;
    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @Nullable
    private String hostname;
    /**
     * The port of the request, e.g. 443
     */
    private int port = -1;
    /**
     * The path of the request, e.g. '/search'
     */
    @Nullable
    private String pathname;
    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    @Nullable
    private String search;

    /**
     * The protocol of the request, e.g. 'https:'.
     */
    @Nullable
    public String getProtocol() {
        return protocol;
    }

    /**
     * The protocol of the request, e.g. 'https:'.
     */
    public Url withProtocol(@Nullable String protocol) {
        this.protocol = protocol;
        return this;
    }

    /**
     * The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    public StringBuilder getFull() {
        return full;
    }

    /**
     * Updates full URL from current state of {@literal this}. Must be called after all other Url fields are set.
     *
     * @return url
     */
    public Url updateFull() {
        // inspired by org.apache.catalina.connector.Request.getRequestURL

        boolean isHttps = "https".equals(protocol);
        boolean isHttp = "http".equals(protocol);
        int portValue = port;
        if (portValue < 0) {
            portValue = isHttps ? 443 : isHttp ? 80 : portValue; // Work around java.net.URL bug
        }

        full.setLength(0);
        full.append(protocol);
        full.append("://");
        full.append(hostname);
        if ((isHttps && portValue != 443) || (isHttp && portValue != 80)) {
            full.append(':');
            full.append(portValue);
        }
        full.append(pathname);
        if (search != null) {
            full.append('?').append(search);
        }
        return this;
    }

    public Url appendToFull(CharSequence charSequence) {
        full.append(charSequence);
        return this;
    }

    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @Nullable
    public String getHostname() {
        return hostname;
    }

    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    public Url withHostname(@Nullable String hostname) {
        this.hostname = hostname;
        return this;
    }

    /**
     * The port of the request, e.g. 443
     */
    public int getPort() {
        return port;
    }

    /**
     * The port of the request, e.g. 443
     */
    public Url withPort(int port) {
        this.port = port;
        return this;
    }

    /**
     * The path of the request, e.g. '/search'
     */
    @Nullable
    public String getPathname() {
        return pathname;
    }

    /**
     * The path of the request, e.g. '/search'
     */
    public Url withPathname(@Nullable String pathname) {
        this.pathname = pathname;
        return this;
    }

    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    @Nullable
    public String getSearch() {
        return search;
    }

    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    public Url withSearch(@Nullable String search) {
        this.search = search;
        return this;
    }

    @Override
    public void resetState() {
        protocol = null;
        full.setLength(0);
        hostname = null;
        port = -1;
        pathname = null;
        search = null;
    }

    public void copyFrom(Url other) {
        this.protocol = other.protocol;
        this.full.setLength(0);
        this.full.append(other.full);
        this.hostname = other.hostname;
        this.port = other.port;
        this.pathname = other.pathname;
        this.search = other.search;
    }

    public boolean hasContent() {
        return protocol != null ||
            full.length() > 0 ||
            hostname != null ||
            port >= 0 ||
            pathname != null ||
            search != null;
    }
}
