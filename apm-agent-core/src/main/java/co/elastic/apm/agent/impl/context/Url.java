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

import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;


public class Url implements Recyclable, co.elastic.apm.agent.tracer.metadata.Url {

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

    @Override
    public Url withProtocol(@Nullable String protocol) {
        this.protocol = protocol;
        return this;
    }

    /**
     * The full, possibly agent-assembled URL of the request, e.g https://example.com:443/search?q=elasticsearch#top.
     */
    public StringBuilder getFull() {
        if (full.length() == 0 && hasContent()) {
            updateFull();
        }
        return full;
    }

    /**
     * Updates full URL from current state of {@literal this}. Must be called after all other Url fields are set.
     */
    private void updateFull() {
        // inspired by org.apache.catalina.connector.Request.getRequestURL

        int portValue = normalizePort(port, protocol);

        full.setLength(0);
        full.append(protocol);
        full.append("://");
        full.append(hostname);
        if ((isHttps(protocol) && portValue != 443) || (isHttp(protocol) && portValue != 80)) {
            full.append(':').append(portValue);
        }
        if (pathname != null) {
            full.append(pathname);
        }
        if (search != null) {
            full.append('?').append(search);
        }
    }

    /**
     * Sets the full URL from an arbitrary string. The value is only parsed if the URL might contain user credentials
     * for sanitizing, otherwise it's copied as-is without allocation.
     *
     * @param value full URL
     * @return this
     */
    public Url withFull(CharSequence value) {
        if (!urlNeedsSanitization(value)) {
            full.setLength(0);
            full.append(value);
        } else {
            // likely needs to be sanitized, thus parsing is simpler
            String uriStringValue = value.toString();
            URI uri = null;
            try {
                uri = new URI(uriStringValue);
            } catch (URISyntaxException e) {
                // silently ignore URIs that we can't parse
            }
            if (uri != null) {
                fillFrom(uri);
            }
        }
        return this;
    }

    private static boolean urlNeedsSanitization(CharSequence sequence) {
        for (int i = 0; i < sequence.length(); i++) {
            if (sequence.charAt(i) == '@') {
                return true;
            }
        }
        return false;
    }

    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    @Nullable
    public String getHostname() {
        return hostname;
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
    public Url withSearch(@Nullable String search) {
        this.search = search;
        return this;
    }

    @Override
    public void fillFrom(URI uri) {
        withProtocol(uri.getScheme())
            .withHostname(uri.getHost())
            .withPort(normalizePort(uri.getPort(), uri.getScheme()))
            .withPathname(uri.getPath())
            .withSearch(uri.getQuery())
            .updateFull();
    }

    /**
     * Fills all attributes of Url from {@link URL} instance, also updates full
     *
     * @param url URL
     */
    public void fillFrom(URL url) {
        int port = url.getPort();
        if (port < 0) {
            port = url.getDefaultPort();
        }

        withProtocol(url.getProtocol())
            .withHostname(url.getHost())
            .withPort(port)
            .withPathname(url.getPath())
            .withSearch(url.getQuery())
            .updateFull();
    }

    @Override
    public void fillFrom(@Nullable String protocol,
                         @Nullable String hostname,
                         int port,
                         @Nullable String pathname,
                         @Nullable String search) {
        withProtocol(protocol)
            .withHostname(hostname)
            .withPort(normalizePort(port, protocol))
            .withPathname(pathname)
            .withSearch(search)
            .updateFull();
    }

    @Deprecated
    public void fillFrom(CharSequence uriString) {
        full.setLength(0);
        full.append(uriString);
    }

    /**
     * Parses the full property as URL and populates individual properties
     */
    public void parseAndFillFromFull() {
        if (full.length() > 0) {
            try {
                fillFrom(new URL(full.toString()));
            } catch (MalformedURLException ignore) {
            }
        }
    }

    public static int normalizePort(int port, @Nullable String protocol) {
        int portValue = port;
        if (portValue < 0 && protocol != null) {
            // Work around java.net.URL bug
            // When port is implicit its value is set to -1 and not the one we expect
            portValue = isHttps(protocol) ? 443 : isHttp(protocol) ? 80 : portValue;
        }
        return portValue;
    }

    private static boolean isHttps(@Nullable String protocol) {
        return "https".equals(protocol);
    }

    private static boolean isHttp(@Nullable String protocol) {
        return "http".equals(protocol);
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
