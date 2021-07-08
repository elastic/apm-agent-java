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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class TransactionNameUtilsTest extends AbstractInstrumentationTest {

    private WebConfiguration webConfig;

    @BeforeEach
    void beforeEach() {
        webConfig = config.getConfig(WebConfiguration.class);
        when(webConfig.isUsePathAsName()).thenReturn(true);
    }

    @ParameterizedTest
    @CsvSource({
        "GET",
        "POST",
        "PUT",
        "DELETE",
        "HEAD",
        "OPTIONS",
        "TRACE",
        "OTHER"})
    void testServletClassMapping(String httpMethod) {

        StringBuilder sb = new StringBuilder();
        TransactionNameUtils.setTransactionNameByServletClass(httpMethod, TransactionNameUtilsTest.class, sb);

        String methodName = String.format("do%s%s", httpMethod.charAt(0), httpMethod.substring(1).toLowerCase(Locale.ROOT));

        if (httpMethod.equals("OTHER")) {
            methodName = httpMethod;
        }

        assertThat(sb.toString()).isEqualTo("TransactionNameUtilsTest#%s", methodName);
    }

    @Test
    void testServletClassMapping() {
        StringBuilder sb = new StringBuilder();

        // both should be no-op
        TransactionNameUtils.setTransactionNameByServletClass("GET", TransactionNameUtilsTest.class, null);
        TransactionNameUtils.setTransactionNameByServletClass("GET", null, sb);
        assertThat(sb).isEmpty();

        // no http method provided
        TransactionNameUtils.setTransactionNameByServletClass(null, TransactionNameUtilsTest.class, sb);
        assertThat(sb.toString()).isEqualTo("TransactionNameUtilsTest");
    }

    @Test
    void testClassAndMethodName() {
        // should be a no-op
        TransactionNameUtils.setNameFromClassAndMethod("ClassName", "methodName", null);

        StringBuilder sb = new StringBuilder();
        TransactionNameUtils.setNameFromClassAndMethod("ClassName", "methodName", sb);
        assertThat(sb.toString()).isEqualTo("ClassName#methodName");

        sb.setLength(0);
        TransactionNameUtils.setNameFromClassAndMethod("ClassName", null, sb);
        assertThat(sb.toString()).isEqualTo("ClassName");
    }

    @Test
    void setNameFromHttpRequestPath() {
        List<WildcardMatcher> urlGroups = List.of(
            WildcardMatcher.valueOf("/foo/bar/*/qux"),
            WildcardMatcher.valueOf("/foo/bar/*")
        );

        // shuold be a no-op
        TransactionNameUtils.setNameFromHttpRequestPath("GET", "", "", null, urlGroups);

        testHttpRequestPath("GET", "/hello", "/world", urlGroups, "GET /hello/world");
        testHttpRequestPath("POST", "/hello", null, urlGroups, "POST /hello");

        testHttpRequestPath("GET", "/foo/bar/baz", null, urlGroups, "GET /foo/bar/*");
        testHttpRequestPath("GET", "/foo", "/bar/baz", urlGroups, "GET /foo/bar/*");
        testHttpRequestPath("POST", "/foo/bar/baz/qux", null, urlGroups, "POST /foo/bar/*/qux");
        testHttpRequestPath("GET", "/foo/bar/baz/quux", null, urlGroups, "GET /foo/bar/*");
        testHttpRequestPath("GET", "/foo/bar/baz/quux/qux", null, urlGroups, "GET /foo/bar/*/qux");

    }

    private void testHttpRequestPath(String httpMethod, String firstPart, @Nullable String secondPart, List<WildcardMatcher> urlGroups, String expected) {
        StringBuilder sb = new StringBuilder();
        TransactionNameUtils.setNameFromHttpRequestPath(httpMethod, firstPart, secondPart, sb, urlGroups);
        assertThat(sb.toString()).isEqualTo(expected);
    }


}
