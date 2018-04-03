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
package co.elastic.apm.servlet;

import co.elastic.apm.MockReporter;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.configuration.WebConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Url;
import co.elastic.apm.util.PotentiallyMultiValuedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApmFilterTest {

    private ApmFilter apmFilter;
    private MockReporter reporter;
    private ConfigurationRegistry config;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        ElasticApmTracer tracer = ElasticApmTracer.builder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        apmFilter = new ApmFilter(tracer);
    }

    @Test
    void testEndsTransaction() throws IOException, ServletException {
        apmFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    @Test
    void testDisabled() throws IOException, ServletException {
        when(config.getConfig(CoreConfiguration.class).isActive()).thenReturn(false);
        apmFilter = spy(apmFilter);
        apmFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getTransactions()).hasSize(0);
        verify(apmFilter, never()).captureTransaction(any(), any(), any());
    }

    @Test
    void testURLTransaction() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/foo/bar");
        request.setQueryString("foo=bar");
        apmFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        Url url = reporter.getFirstTransaction().getContext().getRequest().getUrl();
        assertThat(url.getProtocol()).isEqualTo("http");
        assertThat(url.getSearch()).isEqualTo("foo=bar");
        assertThat(url.getPort()).isEqualTo("80");
        assertThat(url.getHostname()).isEqualTo("localhost");
        assertThat(url.getPathname()).isEqualTo("/foo/bar");
        assertThat(url.getFull().toString()).isEqualTo("http://localhost/foo/bar?foo=bar");
    }

    @Test
    void getResult() {
        assertSoftly(softly -> {
            softly.assertThat(apmFilter.getResult(100)).isEqualTo("HTTP 1xx");
            softly.assertThat(apmFilter.getResult(199)).isEqualTo("HTTP 1xx");
            softly.assertThat(apmFilter.getResult(200)).isEqualTo("HTTP 2xx");
            softly.assertThat(apmFilter.getResult(299)).isEqualTo("HTTP 2xx");
            softly.assertThat(apmFilter.getResult(300)).isEqualTo("HTTP 3xx");
            softly.assertThat(apmFilter.getResult(399)).isEqualTo("HTTP 3xx");
            softly.assertThat(apmFilter.getResult(400)).isEqualTo("HTTP 4xx");
            softly.assertThat(apmFilter.getResult(499)).isEqualTo("HTTP 4xx");
            softly.assertThat(apmFilter.getResult(500)).isEqualTo("HTTP 5xx");
            softly.assertThat(apmFilter.getResult(599)).isEqualTo("HTTP 5xx");
            softly.assertThat(apmFilter.getResult(600)).isNull();
            softly.assertThat(apmFilter.getResult(20)).isNull();
            softly.assertThat(apmFilter.getResult(0)).isNull();
            softly.assertThat(apmFilter.getResult(-1)).isNull();
        });
    }

    @Test
    void testTrackPostParams() throws IOException, ServletException {
        when(config.getConfig(WebConfiguration.class).getCaptureBody()).thenReturn(WebConfiguration.EventType.ALL);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/foo/bar");
        request.addParameter("foo", "bar");
        request.addParameter("baz", "qux", "quux");
        request.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=uft-8");

        apmFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getBody()).isInstanceOf(PotentiallyMultiValuedMap.class);
        PotentiallyMultiValuedMap<String, String> params = (PotentiallyMultiValuedMap<String, String>) reporter.getFirstTransaction()
            .getContext().getRequest().getBody();
        assertThat(params.get("foo")).isEqualTo("bar");
        assertThat(params.get("baz")).isEqualTo(Arrays.asList("qux", "quux"));
    }

    @Test
    void captureException() throws IOException, ServletException {
        final Servlet servlet = mock(Servlet.class);
        doThrow(new ServletException("Bazinga")).when(servlet).service(any(), any());
        assertThatThrownBy(() -> apmFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain(servlet)))
            .isInstanceOf(ServletException.class);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(reporter.getFirstError().getException().getMessage()).isEqualTo("Bazinga");
    }
}
