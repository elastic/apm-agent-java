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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.util.TransactionNameUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_LOW_LEVEL_FRAMEWORK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ServletTransactionHelperTest extends AbstractInstrumentationTest {

    private ServletTransactionHelper servletTransactionHelper;
    private WebConfiguration webConfig;

    @BeforeEach
    void setUp() {
        webConfig = config.getConfig(WebConfiguration.class);
        servletTransactionHelper = new ServletTransactionHelper(tracer);
    }

    @Test
    void setTransactionNameByServletClass() {
        Transaction transaction = new Transaction(MockTracer.create());
        TransactionNameUtils.setTransactionNameByServletClass("GET", ServletTransactionHelperTest.class, transaction.getAndOverrideName(PRIORITY_LOW_LEVEL_FRAMEWORK));
        assertThat(transaction.getNameAsString()).isEqualTo("ServletTransactionHelperTest#doGet");
    }

    @Test
    void setTransactionNameByServletClassNullMethod() {
        Transaction transaction = new Transaction(MockTracer.create());
        TransactionNameUtils.setTransactionNameByServletClass(null, ServletTransactionHelperTest.class, transaction.getAndOverrideName(PRIORITY_LOW_LEVEL_FRAMEWORK));
        assertThat(transaction.getNameAsString()).isEqualTo("ServletTransactionHelperTest");
    }

    @Test
    void testGroupUrls() {
        doReturn(true).when(webConfig).isUsePathAsName();
        doReturn(List.of(
            WildcardMatcher.valueOf("/foo/bar/*/qux"),
            WildcardMatcher.valueOf("/foo/bar/*")
        )).when(webConfig).getUrlGroups();

        assertThat(getTransactionName("GET", "/foo/bar/baz")).isEqualTo("GET /foo/bar/*");
        assertThat(getTransactionName("POST", "/foo/bar/baz/qux")).isEqualTo("POST /foo/bar/*/qux");
        assertThat(getTransactionName("GET", "/foo/bar/baz/quux")).isEqualTo("GET /foo/bar/*");
        assertThat(getTransactionName("GET", "/foo/bar/baz/quux/qux")).isEqualTo("GET /foo/bar/*/qux");
    }

    @Test
    void testGroupUrlsOverridesServletName() {
        doReturn(true).when(webConfig).isUsePathAsName();
        doReturn(List.of(
            WildcardMatcher.valueOf("/foo/bar/*")
        )).when(webConfig).getUrlGroups();

        Transaction transaction = new Transaction(MockTracer.create());
        TransactionNameUtils.setTransactionNameByServletClass("GET", ServletTransactionHelperTest.class, transaction.getAndOverrideName(PRIORITY_LOW_LEVEL_FRAMEWORK));
        servletTransactionHelper.applyDefaultTransactionName("GET", "/foo/bar/baz", null, transaction);
        assertThat(transaction.getNameAsString()).isEqualTo("GET /foo/bar/*");
    }

    @Nonnull
    private String getTransactionName(String method, String path) {
        Transaction transaction = new Transaction(MockTracer.create());
        servletTransactionHelper.applyDefaultTransactionName(method, path, null, transaction);
        return transaction.getNameAsString();
    }

    @Test
    void testServletPathNormalization() {
        // use servlet path when provided and not empty
        assertThat(servletTransactionHelper.normalizeServletPath("/ignored/ignored-servlet", "/ignored", "/servlet", null)).isEqualTo("/servlet");

        Stream.of("", null).forEach(
            servletPath -> {
                // reconstruct servlet path from URI
                assertThat(servletTransactionHelper.normalizeServletPath("/context/servlet", "/context", servletPath, null)).isEqualTo("/servlet");

                // reconstruct servlet path from URI with empty/null/root context path
                assertThat(servletTransactionHelper.normalizeServletPath("/servlet", "", servletPath, null)).isEqualTo("/servlet");
                assertThat(servletTransactionHelper.normalizeServletPath("/servlet", "/", servletPath, null)).isEqualTo("/servlet");
                assertThat(servletTransactionHelper.normalizeServletPath("/servlet", null, servletPath, null)).isEqualTo("/servlet");

                // reconstruct servlet path from URI with empty/null/root context path + path info
                assertThat(servletTransactionHelper.normalizeServletPath("/context/servlet/info", "/context", servletPath, "/info")).isEqualTo("/servlet");
                assertThat(servletTransactionHelper.normalizeServletPath("/servlet/info", "/", servletPath, "/info")).isEqualTo("/servlet");
                assertThat(servletTransactionHelper.normalizeServletPath("/servlet/info", null, servletPath, "/info")).isEqualTo("/servlet");

                // limit case where the complete requestURI equals the context path
                assertThat(servletTransactionHelper.normalizeServletPath("/context/servlet", "/context/servlet", servletPath, null)).isEqualTo("/context/servlet");
                assertThat(servletTransactionHelper.normalizeServletPath("/context/servlet", "/context/servlet", servletPath, "")).isEqualTo("/context/servlet");

                // limit case where the pathInfo contains the request path, the servlet path should be empty
                assertThat(servletTransactionHelper.normalizeServletPath("/request/uri", null, servletPath, "/request/uri")).isEqualTo("");
                assertThat(servletTransactionHelper.normalizeServletPath("/request/uri", "", servletPath, "/request/uri")).isEqualTo("");

            }
        );

    }

    @Test
    void testGetUserFromPrincipal() {
        assertThat(ServletTransactionHelper.getUserFromPrincipal(null)).isNull();

        Principal noNamePrincipal = mock(Principal.class);
        assertThat(ServletTransactionHelper.getUserFromPrincipal(noNamePrincipal)).isNull();

        Principal principalWithName = mock(Principal.class);
        doReturn("bob").when(principalWithName).getName();
        assertThat(ServletTransactionHelper.getUserFromPrincipal(principalWithName)).isEqualTo("bob");
    }

    @Test
    void testGetUserFromPrincipal_azureSSO() {

        AzurePrincipal principal = new AzurePrincipal();
        assertThat(ServletTransactionHelper.getUserFromPrincipal(principal)).isNull();

        principal = new AzurePrincipal();
        principal.put("name", Collections.singletonList("bob"));
        assertThat(ServletTransactionHelper.getUserFromPrincipal(
            principal))
            .isEqualTo("bob");

        principal = new AzurePrincipal();
        principal.put("preferred_username", Collections.singletonList("joe"));
        assertThat(ServletTransactionHelper.getUserFromPrincipal(principal))
            .isEqualTo("joe");

        principal = new AzurePrincipal();
        principal.put("preferred_username", Collections.singletonList("joe"));
        principal.put("name", Collections.singletonList("bob"));
        assertThat(ServletTransactionHelper.getUserFromPrincipal(principal))
            .describedAs("preferred_username has priority over name")
            .isEqualTo("joe");


        // defensively guard against empty claim collection
        principal = new AzurePrincipal();
        principal.put("name", Collections.singletonList("joe"));
        principal.put("preferred_username", Collections.emptyList());
        assertThat(ServletTransactionHelper.getUserFromPrincipal(principal))
            .isEqualTo("joe");

    }

    /**
     * Mockup of Azure SSO principal which is also a Map and does return an empty name
     */
    private static final class AzurePrincipal extends HashMap<String, Collection<String>> implements Principal {

        @Override
        public String getName() {
            return "";
        }

    }

}
