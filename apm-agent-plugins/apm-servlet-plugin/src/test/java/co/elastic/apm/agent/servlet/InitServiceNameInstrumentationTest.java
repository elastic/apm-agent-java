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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockServletConfig;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class InitServiceNameInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testServletInit() {
        Servlet servlet = new HttpServlet() {
        };

        CustomManifestLoader cl = new CustomManifestLoader(() -> getClass().getResourceAsStream("/TEST-MANIFEST.MF"));
        CustomManifestLoader.withThreadContextClassLoader(cl, () -> {
            servlet.init(new MockServletConfig());
            tracer.startRootTransaction(cl).end();
        });

        assertServiceInfo();
    }

    @Test
    void testFilterInit() {
        Filter filter = new NoopFilter();

        CustomManifestLoader cl = new CustomManifestLoader(() -> getClass().getResourceAsStream("/TEST-MANIFEST.MF"));
        CustomManifestLoader.withThreadContextClassLoader(cl, () -> {
            filter.init(new MockFilterConfig());
            tracer.startRootTransaction(cl).end();
        });

        assertServiceInfo();
    }

    private void assertServiceInfo() {
        TraceContext traceContext = reporter.getFirstTransaction().getTraceContext();
        assertThat(traceContext.getServiceName()).isEqualTo("service-name-from-manifest");
        assertThat(traceContext.getServiceVersion()).isEqualTo("1.42.0");
    }

    private static class NoopFilter implements Filter {
        @Override
        public void init(FilterConfig filterConfig) {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {

        }
    }
}
