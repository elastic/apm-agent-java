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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.DestinationImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

@SuppressWarnings("ConstantConditions")
class HttpClientHelperTest extends AbstractInstrumentationTest {

    @BeforeEach
    void beforeTest() {
        startTestRootTransaction("Test HTTP client");
    }

    @AfterEach
    void afterTest() {
        tracer.currentTransaction().deactivate().end();
    }

    @Test
    void testNonDefaultPort() throws URISyntaxException {
        createSpanWithUrl("http://user:pass@testing.local:1234/path?query");
        assertThat(reporter.getSpans()).hasSize(1);
        SpanImpl httpSpan = reporter.getFirstSpan();
        assertThat(httpSpan.getContext().getHttp().getUrl().toString()).isEqualTo("http://testing.local:1234/path?query");
        DestinationImpl destination = httpSpan.getContext().getDestination();
        assertThat(httpSpan.getContext().getServiceTarget())
            .hasType("http")
            .hasName("testing.local:1234")
            .hasNameOnlyDestinationResource();

        assertThat(destination.getAddress().toString()).isEqualTo("testing.local");
        assertThat(destination.getPort()).isEqualTo(1234);
    }

    @Test
    void testDefaultExplicitPort() throws URISyntaxException {
        createSpanWithUrl("https://www.elastic.co:443/products/apm");
        assertThat(reporter.getSpans()).hasSize(1);
        SpanImpl httpSpan = reporter.getFirstSpan();
        assertThat(httpSpan.getContext().getHttp().getUrl().toString()).isEqualTo("https://www.elastic.co:443/products/apm");
        DestinationImpl destination = httpSpan.getContext().getDestination();
        assertThat(httpSpan.getContext().getServiceTarget())
            .hasType("http")
            .hasName("www.elastic.co:443")
            .hasNameOnlyDestinationResource();
        assertThat(destination.getAddress().toString()).isEqualTo("www.elastic.co");
        assertThat(destination.getPort()).isEqualTo(443);
    }

    @Test
    void testDefaultImplicitPort() throws URISyntaxException {
        createSpanWithUrl("https://www.elastic.co/products/apm");
        assertThat(reporter.getSpans()).hasSize(1);
        SpanImpl httpSpan = reporter.getFirstSpan();
        assertThat(httpSpan.getContext().getHttp().getUrl().toString()).isEqualTo("https://www.elastic.co/products/apm");
        assertThat(httpSpan.getContext().getServiceTarget())
            .hasType("http")
            .hasName("www.elastic.co:443")
            .hasNameOnlyDestinationResource();
        DestinationImpl destination = httpSpan.getContext().getDestination();
        assertThat(destination.getAddress().toString()).isEqualTo("www.elastic.co");
        assertThat(destination.getPort()).isEqualTo(443);
    }

    @Test
    void testDefaultImplicitPortWithIpv4() throws URISyntaxException {
        createSpanWithUrl("https://151.101.114.217/index.html");
        assertThat(reporter.getSpans()).hasSize(1);
        SpanImpl httpSpan = reporter.getFirstSpan();
        assertThat(httpSpan.getContext().getHttp().getUrl().toString()).isEqualTo("https://151.101.114.217/index.html");
        assertThat(httpSpan.getContext().getServiceTarget())
            .hasType("http")
            .hasName("151.101.114.217:443")
            .hasNameOnlyDestinationResource();
        DestinationImpl destination = httpSpan.getContext().getDestination();
        assertThat(destination.getAddress().toString()).isEqualTo("151.101.114.217");
        assertThat(destination.getPort()).isEqualTo(443);
    }

    @Test
    void testDefaultImplicitPortWithIpv6() throws URISyntaxException {
        createSpanWithUrl("http://[2001:db8:a0b:12f0::1]/index.html");
        assertThat(reporter.getSpans()).hasSize(1);
        SpanImpl httpSpan = reporter.getFirstSpan();
        assertThat(httpSpan.getContext().getHttp().getUrl().toString()).isEqualTo("http://[2001:db8:a0b:12f0::1]/index.html");
        DestinationImpl destination = httpSpan.getContext().getDestination();
        assertThat(destination.getAddress().toString()).isEqualTo("2001:db8:a0b:12f0::1");
        assertThat(destination.getPort()).isEqualTo(80);
        assertThat(httpSpan.getContext().getServiceTarget())
            .hasType("http")
            .hasName("[2001:db8:a0b:12f0::1]:80")
            .hasNameOnlyDestinationResource();
    }

    private void createSpanWithUrl(String s) throws URISyntaxException {
        HttpClientHelper.startHttpClientSpan(tracer.getActive(), "GET", new URI(s), null)
            .end();
    }

    @Test
    void testContentTypeCharsetExtraction() {
        assertThat(HttpClientHelper.extractCharsetFromContentType("multipart/form-data; boundary=---------------------------974767299852498929531610575"))
            .isNull();
        assertThat(HttpClientHelper.extractCharsetFromContentType("Content-Type: text/html; charset=utf-8"))
            .isEqualTo("utf-8");
        assertThat(HttpClientHelper.extractCharsetFromContentType("Content-Type: text/html; charset = foobar;baz"))
            .isEqualTo("foobar");
        assertThat(HttpClientHelper.extractCharsetFromContentType("Content-Type: application/json; charset = \"foo bar\";baz"))
            .isEqualTo("foo bar");

    }
}
