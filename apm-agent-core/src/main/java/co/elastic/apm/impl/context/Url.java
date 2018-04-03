/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.impl.context;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;


/**
 * A complete Url, with scheme, host and path.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Url implements Recyclable {

    /**
     * The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    @JsonProperty("full")
    private final StringBuilder full = new StringBuilder();
    /**
     * The protocol of the request, e.g. 'https:'.
     */
    @Nullable
    @JsonProperty("protocol")
    private String protocol;
    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @Nullable
    @JsonProperty("hostname")
    private String hostname;
    /**
     * The port of the request, e.g. '443'
     */
    @Nullable
    @JsonProperty("port")
    private String port;
    /**
     * The path of the request, e.g. '/search'
     */
    @Nullable
    @JsonProperty("pathname")
    private String pathname;
    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    @Nullable
    @JsonProperty("search")
    private String search;

    /**
     * The protocol of the request, e.g. 'https:'.
     */
    @Nullable
    @JsonProperty("protocol")
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
    @JsonProperty("full")
    public StringBuilder getFull() {
        return full;
    }

    public Url appendToFull(CharSequence charSequence) {
        full.append(charSequence);
        return this;
    }

    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @Nullable
    @JsonProperty("hostname")
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
     * The port of the request, e.g. '443'
     */
    @Nullable
    @JsonProperty("port")
    public String getPort() {
        return port;
    }

    /**
     * The port of the request, e.g. '443'
     */
    public Url withPort(@Nullable String port) {
        this.port = port;
        return this;
    }

    /**
     * The path of the request, e.g. '/search'
     */
    @Nullable
    @JsonProperty("pathname")
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
    @JsonProperty("search")
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
        port = null;
        pathname = null;
        search = null;
    }

    public void copyFrom(Url other) {
        this.protocol = other.protocol;
        this.full.append(other.full);
        this.hostname = other.hostname;
        this.port = other.port;
        this.pathname = other.pathname;
        this.search = other.search;
    }
}
