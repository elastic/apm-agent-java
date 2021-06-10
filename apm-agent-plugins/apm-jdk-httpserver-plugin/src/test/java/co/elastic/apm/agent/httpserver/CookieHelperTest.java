/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.httpserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CookieHelperTest {

    @ParameterizedTest
    @ValueSource(strings = {"yummy_cookie=choco", "yummy_cookie=\"choco\"", "$Version=\"1\"; yummy_cookie=choco; $Domain=localhost; $Path=/"})
    void testSingleValue(String headerValue) {
        List<String[]> cookies = CookieHelper.getCookies(List.of(headerValue));
        assertThat(cookies).hasSize(1);
        assertThat(cookies.get(0)).containsExactly("yummy_cookie", "choco");
    }

    @ParameterizedTest
    @ValueSource(strings = {"yummy_cookie=choco; tasty_cookie=strawberry", "yummy_cookie=\"choco\"; tasty_cookie=\"strawberry\"", "$Version=\"1\"; yummy_cookie=choco; $Domain=localhost; $Path=/; tasty_cookie=strawberry; $Domain=localhost; $Path=/"})
    void testMultipleValues(String headerValue) {
        List<String[]> cookies = CookieHelper.getCookies(List.of(headerValue));
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0)).containsExactly("yummy_cookie", "choco");
        assertThat(cookies.get(1)).containsExactly("tasty_cookie", "strawberry");
    }

    @Test
    void testList() {
        List<String[]> cookies = CookieHelper.getCookies(List.of("yummy_cookie=choco", "tasty_cookie=strawberry"));
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0)).containsExactly("yummy_cookie", "choco");
        assertThat(cookies.get(1)).containsExactly("tasty_cookie", "strawberry");
    }
}
