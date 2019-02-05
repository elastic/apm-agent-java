/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.AbstractServletTest;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import net.bytebuddy.agent.ByteBuddyAgent;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ForwardRequestDispatcherInstrumentationTest extends AbstractServletTest {

    @AfterEach
    final void afterEach() {
        ElasticApmAgent.reset();
    }

    @Override
    protected void setUpHandler(ServletContextHandler handler) {
        handler.addServlet(ServletInstrumentationTest.TestServlet.class, "/test");
        handler.addServlet(ServletInstrumentationTest.ForwardingServlet.class, "/forward");
    }

    @Test
    void testForward() throws Exception {
        testInstrumentation(Arrays.asList(new ForwardRequestDispatcherInstrumentation(), new ServletInstrumentation()), 1, "/forward");
    }

    private void testInstrumentation(List<ElasticApmInstrumentation> instrumentations, int expectedTransactions, String path) throws IOException, InterruptedException {
        initInstrumentation(instrumentations);

        final Response response = get(path);

        assertThat(response.code()).isEqualTo(200);

        if (expectedTransactions > 0) {
            reporter.getFirstTransaction(500);
        }
        System.out.println("Transaction name="+reporter.getFirstTransaction().getName().toString());
        assertThat(reporter.getTransactions()).hasSize(expectedTransactions);
        assertThat(reporter.getSpans().size()).isEqualTo(1);
        assertEquals("FORWARD /forward", reporter.getSpans().get(0).getName().toString());
    }

    private void initInstrumentation(List<ElasticApmInstrumentation> instrumentations) {
        ElasticApmAgent.initInstrumentation(new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build(), ByteBuddyAgent.install(), instrumentations);
    }

}
