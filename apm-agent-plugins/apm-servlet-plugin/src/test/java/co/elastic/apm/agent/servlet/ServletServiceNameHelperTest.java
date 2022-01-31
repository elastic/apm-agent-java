/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.servlet.adapter.JavaxServletApiAdapter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;

import javax.servlet.ServletContext;

import static org.assertj.core.api.Assertions.assertThat;

class ServletServiceNameHelperTest {

    private final MockReporter reporter = new MockReporter();
    private final ElasticApmTracer tracer = MockTracer.createRealTracer(reporter);

    @BeforeEach
    void setUp() {
    }

    /**
     * Tests a scenario of un-deploying a webapp and then re-deploying it on a Servlet container
     */
    @Test
    void testServiceNameConsistencyAcrossDifferentClassLoaders() {

        ClassLoader cl1 = new CustomManifestLoader(() -> null);
        CustomManifestLoader.withThreadContextClassLoader(cl1, () -> {
            ServletServiceNameHelper.determineServiceName(JavaxServletApiAdapter.get(), createServletContext(), tracer);
            tracer.startRootTransaction(cl1).end();
        });

        ClassLoader cl2 = new CustomManifestLoader(() -> null);
        CustomManifestLoader.withThreadContextClassLoader(cl2, () -> {
            ServletServiceNameHelper.determineServiceName(JavaxServletApiAdapter.get(), createServletContext(), tracer);
            tracer.startRootTransaction(cl2).end();
        });

        assertThat(reporter.getTransactions()).hasSize(2);
        assertThat(reporter.getTransactions().stream()
            .map(t -> t.getTraceContext().getServiceName())
            .distinct())
            .containsExactly("test-context");
    }

    @Test
    void testServiceNameFromManifest() {
        ClassLoader cl1 = new CustomManifestLoader(() -> getClass().getResourceAsStream("/TEST-MANIFEST.MF"));
        CustomManifestLoader.withThreadContextClassLoader(cl1, () -> {
            ServletServiceNameHelper.determineServiceName(JavaxServletApiAdapter.get(), createServletContext(), tracer);
            tracer.startRootTransaction(cl1).end();
        });
        assertThat(reporter.getFirstTransaction().getTraceContext().getServiceName()).isEqualTo("service-name-from-manifest");
        assertThat(reporter.getFirstTransaction().getTraceContext().getServiceVersion()).isEqualTo("1.42.0");
    }

    @NotNull
    private ServletContext createServletContext() {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setContextPath("test-context-path");
        servletContext.setServletContextName("test-context");
        return servletContext;
    }

}
