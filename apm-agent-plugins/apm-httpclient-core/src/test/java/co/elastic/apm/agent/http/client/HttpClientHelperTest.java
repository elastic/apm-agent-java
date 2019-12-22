package co.elastic.apm.agent.http.client;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
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
        tracer.startTransaction(TraceContext.asRoot(), null, null)
            .withName("Test HTTP client")
            .withType("test")
            .activate();
    }

    @AfterEach
    void afterTest() {
        tracer.currentTransaction().deactivate().end();
        reporter.reset();
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
    }
}
