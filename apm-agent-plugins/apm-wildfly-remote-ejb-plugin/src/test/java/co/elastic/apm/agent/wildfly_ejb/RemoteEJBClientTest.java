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
package co.elastic.apm.agent.wildfly_ejb;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

public class RemoteEJBClientTest extends AbstractInstrumentationTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule();

    @Test
    public void testClientSpan() throws Exception {
        wireMockRule.stubFor(post(urlPathMatching("/wildfly-services/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/x-wf-ejb-jbmar-response;version=1")
                .withBody(new byte[]{2, 1, 0})));

        Transaction transaction = startTestRootTransaction();
        try {
            GreeterManager greeterManager = (GreeterManager) getContext().lookup("ejb:/app/module/BeanName!co.elastic.apm.agent.wildfly_ejb.GreeterManager");
            greeterManager.greet();
        } finally {
            transaction.deactivate().end();
        }

        Span clientSpan = reporter.getFirstSpan(1000L);
        assertThat(clientSpan.getType()).isEqualTo("external");
        assertThat(clientSpan.getSubtype()).isEqualTo("ejb");
        assertThat(clientSpan.getNameAsString()).isEqualTo("GreeterManager#greet");
        assertThat(clientSpan.getContext().getDestination().getAddress().toString()).isEqualTo("localhost");
        assertThat(clientSpan.getContext().getDestination().getPort()).isEqualTo(wireMockRule.getOptions().portNumber());
        assertThat(clientSpan.getContext().getDestination().getService().getType()).isEqualTo("ejb");
        assertThat(clientSpan.getContext().getDestination().getService().getResource().toString()).isEqualTo("localhost:" + wireMockRule.getOptions().portNumber());
        assertThat(clientSpan.getContext().getDestination().getService().getName().toString()).isEqualTo(wireMockRule.baseUrl() + "/wildfly-services");
    }

    private Context getContext() throws NamingException {
        Properties contextProperties = new Properties();
        contextProperties.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
        contextProperties.put(Context.PROVIDER_URL, wireMockRule.baseUrl() + "/wildfly-services");
        contextProperties.put(Context.SECURITY_PRINCIPAL, "ejb-user");
        contextProperties.put(Context.SECURITY_CREDENTIALS, "passw0rd");

        return new InitialContext(contextProperties);
    }
}
