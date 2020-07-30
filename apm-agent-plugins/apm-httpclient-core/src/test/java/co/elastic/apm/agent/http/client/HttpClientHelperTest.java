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
package co.elastic.apm.agent.http.client;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static co.elastic.apm.agent.http.client.HttpClientHelper.EXTERNAL_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ConstantConditions")
class HttpClientHelperTest extends AbstractInstrumentationTest {

    @BeforeEach
    void beforeTest() {
        tracer.startRootTransaction(null)
            .withName("Test HTTP client")
            .withType("test")
            .activate();
    }

    @AfterEach
    void afterTest() {
        tracer.currentTransaction().deactivate().end();
    }

    @Test
    void testNonDefaultPort() throws URISyntaxException {
        HttpClientHelper.startHttpClientSpan(tracer.getActive(), "GET", new URI("http://user:pass@testing.local:1234/path?query"), null)
            .end();
        assertThat(reporter.getSpans()).hasSize(1);
        Span httpSpan = reporter.getFirstSpan();
        assertThat(httpSpan.getContext().getHttp().getUrl()).isEqualTo("http://testing.local:1234/path?query");
        Destination destination = httpSpan.getContext().getDestination();
        assertThat(destination.getService().getName().toString()).isEqualTo("http://testing.local:1234");
        assertThat(destination.getService().getResource().toString()).isEqualTo("testing.local:1234");
        assertThat(destination.getService().getType()).isEqualTo(EXTERNAL_TYPE);
        assertThat(destination.getAddress().toString()).isEqualTo("testing.local");
        assertThat(destination.getPort()).isEqualTo(1234);
    }

    @Test
    void testDefaultExplicitPort() throws URISyntaxException {
        HttpClientHelper.startHttpClientSpan(tracer.getActive(), "GET", new URI("https://www.elastic.co:443/products/apm"), null)
            .end();
        assertThat(reporter.getSpans()).hasSize(1);
        Span httpSpan = reporter.getFirstSpan();
        assertThat(httpSpan.getContext().getHttp().getUrl()).isEqualTo("https://www.elastic.co:443/products/apm");
        Destination destination = httpSpan.getContext().getDestination();
        assertThat(destination.getService().getName().toString()).isEqualTo("https://www.elastic.co");
        assertThat(destination.getService().getResource().toString()).isEqualTo("www.elastic.co:443");
        assertThat(destination.getService().getType()).isEqualTo(EXTERNAL_TYPE);
        assertThat(destination.getAddress().toString()).isEqualTo("www.elastic.co");
        assertThat(destination.getPort()).isEqualTo(443);
    }

    @Test
    void testDefaultImplicitPort() throws URISyntaxException {
        HttpClientHelper.startHttpClientSpan(tracer.getActive(), "GET", new URI("https://www.elastic.co/products/apm"), null)
            .end();
        assertThat(reporter.getSpans()).hasSize(1);
        Span httpSpan = reporter.getFirstSpan();
        assertThat(httpSpan.getContext().getHttp().getUrl()).isEqualTo("https://www.elastic.co/products/apm");
        Destination destination = httpSpan.getContext().getDestination();
        assertThat(destination.getService().getName().toString()).isEqualTo("https://www.elastic.co");
        assertThat(destination.getService().getResource().toString()).isEqualTo("www.elastic.co:443");
        assertThat(destination.getService().getType()).isEqualTo(EXTERNAL_TYPE);
        assertThat(destination.getAddress().toString()).isEqualTo("www.elastic.co");
        assertThat(destination.getPort()).isEqualTo(443);
    }

    @Test
    void testDefaultImplicitPortWithIpv4() throws URISyntaxException {
        HttpClientHelper.startHttpClientSpan(tracer.getActive(), "GET", new URI("https://151.101.114.217/index.html"), null)
            .end();
        assertThat(reporter.getSpans()).hasSize(1);
        Span httpSpan = reporter.getFirstSpan();
        assertThat(httpSpan.getContext().getHttp().getUrl()).isEqualTo("https://151.101.114.217/index.html");
        Destination destination = httpSpan.getContext().getDestination();
        assertThat(destination.getService().getName().toString()).isEqualTo("https://151.101.114.217");
        assertThat(destination.getService().getResource().toString()).isEqualTo("151.101.114.217:443");
        assertThat(destination.getService().getType()).isEqualTo(EXTERNAL_TYPE);
        assertThat(destination.getAddress().toString()).isEqualTo("151.101.114.217");
        assertThat(destination.getPort()).isEqualTo(443);
    }

    @Test
    void testDefaultImplicitPortWithIpv6() throws URISyntaxException {
        HttpClientHelper.startHttpClientSpan(tracer.getActive(), "GET", new URI("http://[2001:db8:a0b:12f0::1]/index.html"), null)
            .end();
        assertThat(reporter.getSpans()).hasSize(1);
        Span httpSpan = reporter.getFirstSpan();
        assertThat(httpSpan.getContext().getHttp().getUrl()).isEqualTo("http://[2001:db8:a0b:12f0::1]/index.html");
        Destination destination = httpSpan.getContext().getDestination();
        assertThat(destination.getService().getName().toString()).isEqualTo("http://[2001:db8:a0b:12f0::1]");
        assertThat(destination.getService().getResource().toString()).isEqualTo("[2001:db8:a0b:12f0::1]:80");
        assertThat(destination.getService().getType()).isEqualTo(EXTERNAL_TYPE);
        assertThat(destination.getAddress().toString()).isEqualTo("2001:db8:a0b:12f0::1");
        assertThat(destination.getPort()).isEqualTo(80);
    }
}
