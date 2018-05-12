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

import co.elastic.apm.util.PotentiallyMultiValuedMap;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ClientIpUtilsTest {

    @Test
    void getRealIp() {
        assertSoftly(softly -> {
            softly.assertThat(ClientIpUtils.getRealIp(getHeaders(Collections.emptyMap()), "foo")).isEqualTo("foo");
            List.of("x-forwarded-for","x-real-ip","proxy-client-ip","wl-proxy-client-ip","http_client_ip","http_x_forwarded_for").forEach(header -> {
                    softly.assertThat(ClientIpUtils.getRealIp(getHeaders(Collections.singletonMap(header, "unknown")), "foo"))
                        .isEqualTo("foo");
                    softly.assertThat(ClientIpUtils.getRealIp(getHeaders(Collections.singletonMap(header, "bar")), "foo"))
                        .isEqualTo("bar");
                    softly.assertThat(ClientIpUtils.getRealIp(getHeaders(Collections.singletonMap(header, "bar, baz")), "foo"))
                        .isEqualTo("bar");
                });
        });
    }

    private PotentiallyMultiValuedMap getHeaders(Map<String, String> headerMap) {
        final PotentiallyMultiValuedMap headers = new PotentiallyMultiValuedMap();
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            headers.add(entry.getKey(), entry.getValue());
        }
        return headers;
    }
}
