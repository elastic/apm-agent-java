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

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Supplier;
import java.util.jar.JarFile;

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
        withThreadContextClassLoader(cl1, () -> {
            ServletServiceNameHelper.determineServiceName(JavaxServletApiAdapter.get(), createRequest(), tracer);
            tracer.startRootTransaction(cl1).end();
        });

        ClassLoader cl2 = new CustomManifestLoader(() -> null);
        withThreadContextClassLoader(cl2, () -> {
            ServletServiceNameHelper.determineServiceName(JavaxServletApiAdapter.get(), createRequest(), tracer);
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
        withThreadContextClassLoader(cl1, () -> {
            ServletServiceNameHelper.determineServiceName(JavaxServletApiAdapter.get(), createRequest(), tracer);
            tracer.startRootTransaction(cl1).end();
        });
        assertThat(reporter.getFirstTransaction().getTraceContext().getServiceName()).isEqualTo("service-name-from-manifest");
        assertThat(reporter.getFirstTransaction().getTraceContext().getServiceVersion()).isEqualTo("1.42.0");
    }

    @NotNull
    private MockHttpServletRequest createRequest() {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setContextPath("test-context-path");
        servletContext.setServletContextName("test-context");
        return new MockHttpServletRequest(servletContext);
    }

    private void withThreadContextClassLoader(ClassLoader contextClassLoader, Runnable runnable) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            runnable.run();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static class CustomManifestLoader extends URLClassLoader {
        private final Supplier<InputStream> manifestSupplier;

        public CustomManifestLoader(Supplier<InputStream> manifestSupplier) {
            super(new URL[0]);
            this.manifestSupplier = manifestSupplier;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if ((JarFile.MANIFEST_NAME).equals(name)) {
                return manifestSupplier.get();
            }
            return super.getResourceAsStream(name);
        }
    }
}
