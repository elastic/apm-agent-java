package co.elastic.apm.servlet;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.annotation.Nonnull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ClientIpUtilsTest {

    @Test
    void getRealIp() {
        assertSoftly(softly -> {
            softly.assertThat(ClientIpUtils.getRealIp(getRequest("foo", Collections.emptyMap()))).isEqualTo("foo");
            List.of("x-forwarded-for","x-real-ip","proxy-client-ip","wl-proxy-client-ip","http_client_ip","http_x_forwarded_for").forEach(header -> {
                    softly.assertThat(ClientIpUtils.getRealIp(getRequest("foo", Collections.singletonMap(header, "unknown"))))
                        .isEqualTo("foo");
                    softly.assertThat(ClientIpUtils.getRealIp(getRequest("foo", Collections.singletonMap(header, "bar"))))
                        .isEqualTo("bar");
                    softly.assertThat(ClientIpUtils.getRealIp(getRequest("foo", Collections.singletonMap(header, "bar, baz"))))
                        .isEqualTo("bar");
                });
        });
    }

    private MockHttpServletRequest getRequest(String remoteAddr, Map<String, String> headers) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }
        return request;
    }
}
