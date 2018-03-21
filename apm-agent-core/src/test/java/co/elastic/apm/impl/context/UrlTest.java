package co.elastic.apm.impl.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlTest {

    @Test
    void testResetState() {
        final Url url = newUrl();
        url.resetState();
        assertThat(url).isEqualTo(new Url());
    }

    @Test
    void testEquals() {
        assertThat(newUrl()).isEqualTo(newUrl());
    }

    @Test
    void testCopyOf() {
        final Url url = newUrl();
        final Url copy = new Url();
        copy.copyFrom(url);
        assertThat(url).isEqualTo(copy);
        assertThat(url).isNotEqualTo(new Url());
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
