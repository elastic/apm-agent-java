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

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

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
