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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.stream.Stream;

import static co.elastic.apm.agent.JsonUtils.toJson;
import static org.assertj.core.api.Assertions.assertThat;

class UrlTest {

    @Test
    void testResetState() {
        final Url url = sampleUrl();
        url.resetState();
        assertThat(toJson(url)).isEqualTo(toJson(new Url()));
    }

    @Test
    void testCopyOf() {
        final Url url = sampleUrl();
        final Url copy = new Url();
        copy.copyFrom(url);
        assertThat(toJson(url)).isEqualTo(toJson(copy));
        assertThat(toJson(url)).isNotEqualTo(toJson(new Url()));
    }

    private Url sampleUrl() {
        return new Url()
            .withHostname("localhost")
            .withPathname("/foo")
            .withPort(8080)
            .withProtocol("http")
            .withSearch("foo=bar");
    }

    @Test
    void computeFullFromProperties() {
        // default port on http
        assertThat(new Url().withProtocol("http")
            .withHostname("localhost")
            .withPort(80)
            .getFull().toString()).isEqualTo("http://localhost");

        // non default port on http + path
        assertThat(new Url().withProtocol("http")
            .withHostname("localhost")
            .withPort(8080)
            .withPathname("/hello")
            .getFull().toString()).isEqualTo("http://localhost:8080/hello");

        // default port on https + search string
        assertThat(new Url().withProtocol("https")
            .withHostname("hostname")
            .withPort(443)
            .withSearch("hello=world")
            .getFull().toString()).isEqualTo("https://hostname?hello=world");

        // non default port on https
        assertThat(new Url().withProtocol("https")
            .withHostname("hostname")
            .withPort(447)
            .getFull().toString()).isEqualTo("https://hostname:447");

    }

    @ParameterizedTest
    @CsvSource(delimiterString = " ", value = {
        // default http port should be 80, but implicit in full
        "http://localhost/path?query=help http://localhost/path?query=help http localhost 80 /path query=help",
        // default https port should be 443, but implicit in full
        "https://localhost/path?query=help https://localhost/path?query=help https localhost 443 /path query=help",
        // non default http port
        "http://localhost:8080/?query=help http://localhost:8080/?query=help http localhost 8080 / query=help",
        // non default https port
        "https://localhost:447/path?query=help https://localhost:447/path?query=help https localhost 447 /path query=help",
        // user credentials are not captured
        "https://user:pwd@localhost:447/ https://localhost:447/ https localhost 447 / "
    })
    void fillFromURIorURL(String uriString, String full, String protocol, String host, int port, String path, String query) throws MalformedURLException {
        Url urlFromUri = new Url();
        URI uri = URI.create(uriString);
        urlFromUri.fillFrom(uri);

        Url urlFromUrl = new Url();
        urlFromUrl.fillFrom(uri.toURL());

        Stream.of(urlFromUri, urlFromUrl).forEach(url -> {
            assertThat(url.getFull().toString()).isEqualTo(full);
            assertThat(url.getProtocol()).isEqualTo(protocol);
            assertThat(url.getHostname()).isEqualTo(host);
            assertThat(url.getPort()).isEqualTo(port);
            assertThat(url.getPathname()).isEqualTo(path);
            assertThat(url.getSearch()).isEqualTo(query);
        });
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " ", value = {
        "http://localhost/path?query=help http://localhost/path?query=help",
        "http://user:password@localhost/path?query=help http://localhost/path?query=help"
    })
    void withFull(String input, String expected) {
        Url url = new Url();
        url.withFull(input);
        assertThat(url.getFull().toString()).isEqualTo(expected);

        // only the 'full' property is always computed
        // other attributes are not parsed unless sanitization is applied (which is an implementation detail)
        if(input.contains("@")){
            assertThat(url.getHostname()).isEqualTo("localhost");
            assertThat(url.getPort()).isEqualTo(80);
        } else {
            assertThat(url.getHostname()).isNull();
            assertThat(url.getPort()).isLessThan(0);
        }

    }

    @Test
    void getFullUpdatesFullWhenRequired() {
        Url url = new Url();

        StringBuilder full = url.getFull();

        url.withProtocol("http").withHostname("localhost").withPathname("/");
        assertThat(full).isEmpty();
        assertThat(url.hasContent()).isTrue();
        assertThat(url.getFull().toString()).isEqualTo("http://localhost/");
    }
}
