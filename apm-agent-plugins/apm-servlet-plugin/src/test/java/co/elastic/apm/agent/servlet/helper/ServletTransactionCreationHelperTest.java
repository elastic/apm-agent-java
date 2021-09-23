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
package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.annotation.Nullable;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServletTransactionCreationHelperTest extends AbstractInstrumentationTest {

    private WebConfiguration webConfig;
    private ServletTransactionCreationHelper helper;

    @BeforeEach
    void setUp() {
        webConfig = config.getConfig(WebConfiguration.class);
        helper = new ServletTransactionCreationHelper(tracer);
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " ", value = {
        "/not-ignored ",
        "/ ",
        " ",
        "/index.html *.xml"})
    void requestPathNotIgnored(String path, String ignoreExpr) {
        checkRequestPathIgnored(path, ignoreExpr, false);
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " ", value = {
        "/ignored/from/prefix /ignored*",
        "/ignored/with-suffix.js *.js",
        "/ignored/with-suffix.html *.js,*.html",
        "/ignored/with/term *with*"})
    void requestPathIgnored(String path, String ignoreExpr) {
        checkRequestPathIgnored(path, ignoreExpr, true);
    }

    void checkRequestPathIgnored(String path, String config, boolean expectIgnored) {
        when(webConfig.getIgnoreUrls())
            .thenReturn(parseWildcard(config));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);

        boolean isIgnored = helper.isExcluded(request);
        assertThat(isIgnored)
            .describedAs("request with path '%s' %s be ignored", expectIgnored ? "should" : "should not", path)
            .isEqualTo(expectIgnored);

    }

    @ParameterizedTest
    @CsvSource(delimiterString = " ", value = {
        "anderson and*",
        "anderson *son",
        "anderson *der*",
        "anderson smith,anderson"
    })
    void requestUserAgentIgnored(String userAgent, String ignoreExpr) {
        when(webConfig.getIgnoreUserAgents())
            .thenReturn(parseWildcard(ignoreExpr));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/request/path");
        request.addHeader("user-agent", userAgent);

        assertThat(helper.isExcluded(request))
            .describedAs("request with user-agent '%s' should be ignored", userAgent)
            .isTrue();
    }

    private static List<WildcardMatcher> parseWildcard(@Nullable String expr) {
        if (null == expr || expr.isEmpty()) {
            return Collections.emptyList();
        }
        return Stream.of(expr.split(","))
            .map(WildcardMatcher::valueOf)
            .collect(Collectors.toList());
    }

    @Test
    void safeGetClassLoader() {
        assertThat(helper.getClassloader(null)).isNull();

        ServletContext servletContext = mock(ServletContext.class);
        doThrow(UnsupportedOperationException.class).when(servletContext).getClassLoader();
        assertThat(helper.getClassloader(servletContext)).isNull();

        servletContext = mock(ServletContext.class);
        ClassLoader cl = mock(ClassLoader.class);
        when(servletContext.getClassLoader()).thenReturn(cl);
        assertThat(helper.getClassloader(servletContext)).isSameAs(cl);
    }
}
