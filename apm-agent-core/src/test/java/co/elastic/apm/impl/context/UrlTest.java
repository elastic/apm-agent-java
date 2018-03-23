package co.elastic.apm.impl.context;

import org.junit.jupiter.api.Test;

import static co.elastic.apm.JsonUtils.toJson;
import static org.assertj.core.api.Assertions.assertThat;

class UrlTest {

    @Test
    void testResetState() {
        final Url url = newUrl();
        url.resetState();
        assertThat(toJson(url)).isEqualTo(toJson(new Url()));
    }

    @Test
    void testCopyOf() {
        final Url url = newUrl();
        final Url copy = new Url();
        copy.copyFrom(url);
        assertThat(toJson(url)).isEqualTo(toJson(copy));
        assertThat(toJson(url)).isNotEqualTo(toJson(new Url()));
    }

    private Url newUrl() {
        return new Url()
            .withHostname("localhost")
            .withPathname("/foo")
            .withPort("8080")
            .withProtocol("http")
            .withSearch("foo=bar")
            .appendToFull("http://localhost:8080/foo?foo=bar");
    }
}
