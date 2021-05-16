/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2021 Elastic and contributors
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
package co.elastic.apm.servlet.tests;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.servlet.AbstractServletContainerIntegrationTest;
import co.elastic.apm.test.GreeterManager;
import com.fasterxml.jackson.databind.JsonNode;
import net.bytebuddy.agent.ByteBuddyAgent;

import javax.naming.Context;
import javax.naming.NamingException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class RemoteEJBTestApp extends TestApp {

    public RemoteEJBTestApp() {
        super("../remote-ejb-app", "remote-ejb-app.war", "/remote-ejb-app/status.html", null);
    }

    @Override
    public void test(AbstractServletContainerIntegrationTest test) throws Exception {
        if (!worksOnImage(test.getImageName())) {
            return;
        }

        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        ElasticApmTracer tracer = mockInstrumentationSetup.getTracer();
        MockReporter reporter = mockInstrumentationSetup.getReporter();

        try {
            ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

            Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader());
            try (Scope scope = transaction.activateInScope()) {
                GreeterManager greeterManager = (GreeterManager) getContext(test).lookup("ejb:/remote-ejb-app/GreeterManagerImpl!co.elastic.apm.test.GreeterManager");
                assertThat(greeterManager.greet()).isEqualTo("Hello World!");

                Span clientSpan = reporter.getFirstSpan(1000L);
                assertThat(clientSpan.getType()).isEqualTo("external");
                assertThat(clientSpan.getSubtype()).isEqualTo("ejb");
                assertThat(clientSpan.getNameAsString()).isEqualTo("GreeterManager#greet");

                List<JsonNode> serverTransactions = test.getReportedTransactions();
                assertThat(serverTransactions.size()).isEqualTo(1);

                JsonNode serverTransaction = serverTransactions.get(0);
                assertThat(serverTransaction.get("trace_id").textValue()).isEqualTo(clientSpan.getTraceContext().getTraceId().toString());
                assertThat(serverTransaction.get("parent_id").textValue()).isEqualTo(clientSpan.getTraceContext().getId().toString());
                assertThat(serverTransaction.get("type").textValue()).isEqualTo("request");
                assertThat(serverTransaction.get("name").textValue()).isEqualTo("GreeterManager#greet");
                assertThat(serverTransaction.get("context").get("service").get("framework").get("name").textValue()).isEqualTo("EJB");

                List<JsonNode> serverSpans = test.getReportedSpans();
                assertThat(serverSpans.size()).isZero();
            } finally {
                transaction.end();
            }
        }finally {
            ElasticApmAgent.reset();
        }
    }

    protected abstract boolean worksOnImage(String imageName);

    protected abstract Context getContext(AbstractServletContainerIntegrationTest test) throws NamingException;
}
