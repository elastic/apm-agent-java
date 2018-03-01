package co.elastic.apm.servlet;

import co.elastic.apm.MockReporter;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.Url;
import co.elastic.apm.util.PotentiallyMultiValuedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
import java.util.ServiceLoader;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ApmFilterTest {

    private ApmFilter apmFilter;
    private MockReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        ElasticApmTracer tracer = ElasticApmTracer.builder()
            .configurationRegistry(ConfigurationRegistry.builder()
                .addConfigSource(new SimpleSource())
                .optionProviders(ServiceLoader.load(ConfigurationOptionProvider.class, ElasticApmTracer.class.getClassLoader()))
                .build())
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
    void testURLTransaction() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/foo/bar");
        request.setQueryString("foo=bar");
        apmFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        Url url = reporter.getFirstTransaction().getContext().getRequest().getUrl();
        assertThat(url.getProtocol()).isEqualTo("http");
        assertThat(url.getSearch()).isEqualTo("foo=bar");
        assertThat(url.getPort()).isEqualTo("80");
        assertThat(url.getHostname()).isEqualTo("localhost");
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
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/foo/bar");
        request.addParameter("foo", "bar");
        request.addParameter("baz", "qux", "quux");
        request.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=uft-8");

        apmFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getBody()).isInstanceOf(PotentiallyMultiValuedMap.class);
        PotentiallyMultiValuedMap<String, String> params = (PotentiallyMultiValuedMap<String, String>) reporter.getFirstTransaction().getContext().getRequest().getBody();
        assertThat(params.get("foo")).isEqualTo("bar");
        assertThat(params.get("baz")).isEqualTo(Arrays.asList("qux", "quux"));
    }
}
