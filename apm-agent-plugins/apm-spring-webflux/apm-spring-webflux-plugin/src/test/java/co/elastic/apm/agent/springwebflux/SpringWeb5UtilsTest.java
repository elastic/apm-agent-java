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
package co.elastic.apm.agent.springwebflux;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class SpringWeb5UtilsTest {

    @Test
    void testGetStatusCode() throws Exception {
        ServerHttpResponse mockResponse = mock(ServerHttpResponse.class);
        doReturn(HttpStatus.IM_USED).when(mockResponse).getStatusCode();
        assertThat(SpringWebVersionUtils.getStatusCode(mockResponse)).isEqualTo(226);
    }

    @Test
    void testWrongResponseType() {
        assertThatThrownBy(() -> SpringWebVersionUtils.getStatusCode(new Object())).isInstanceOf(ClassCastException.class);
    }
}
