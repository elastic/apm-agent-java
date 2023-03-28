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
import java.net.URI;

/**
 * A complete URL, with scheme, host, port, path and query string.
 */
public interface Url {

    /**
     * Fills all attributes of Url from {@link URI} instance, also updates full
     *
     * @param uri URI
     */
    void fillFrom(URI uri);

    void fillFrom(@Nullable String protocol, @Nullable String hostname, int port, @Nullable String pathname, @Nullable String search);

    /**
     * The protocol of the request, e.g. 'https:'.
     */
    Url withProtocol(@Nullable String protocol);

    /**
     * The hostname of the request, e.g. 'example.com'.
     */
    Url withHostname(@Nullable String hostname);

    /**
     * The port of the request, e.g. 443
     */
    Url withPort(int port);

    /**
     * The path of the request, e.g. '/search'
     */
    Url withPathname(@Nullable String pathname);

    /**
     * The search describes the query string of the request. It is expected to have values delimited by ampersands.
     */
    Url withSearch(@Nullable String search);
}
