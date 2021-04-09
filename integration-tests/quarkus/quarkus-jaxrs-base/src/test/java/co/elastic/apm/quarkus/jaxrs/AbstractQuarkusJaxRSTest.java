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
package co.elastic.apm.quarkus.jaxrs;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.api.ElasticApm;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

public abstract class AbstractQuarkusJaxRSTest {

    @Path("/")
    public static class TestApp {

        @GET
        public String greeting() {
            ElasticApm.currentTransaction().setUser("id", "email", "username");
            return "Hello World";
        }
    }

    private static ConfigurationRegistry config;
    private static MockReporter reporter;

    @BeforeAll
    public static void beforeAll() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        config = mockInstrumentationSetup.getConfig();
        when(config.getConfig(ReporterConfiguration.class).isReportSynchronously()).thenReturn(true);
        reporter = mockInstrumentationSetup.getReporter();
        ElasticApmAgent.initInstrumentation(mockInstrumentationSetup.getTracer(), ByteBuddyAgent.install());
    }

    @BeforeEach
    public void setUp() {
        reporter.reset();
    }

    @AfterAll
    public static void afterAll() {
        ElasticApmAgent.reset();
    }

    @Test
    public void greetingShouldReturnDefaultMessage() {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .body(is("Hello World"));

        Transaction transaction = reporter.getFirstTransaction(5000);
        assertThat(transaction.getNameAsString()).isEqualTo("TestApp#greeting");
        assertThat(transaction.getContext().getUser().getId()).isEqualTo("id");
        assertThat(transaction.getContext().getUser().getEmail()).isEqualTo("email");
        assertThat(transaction.getContext().getUser().getUsername()).isEqualTo("username");
        assertThat(transaction.getFrameworkName()).isEqualTo("JAX-RS");
        assertThat(transaction.getFrameworkVersion()).isEqualTo("2.0.1.Final");
    }
}
