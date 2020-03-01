/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.logging.spring.boot;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.ReporterConfiguration;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class SpringBootLogbackSlf4jLoggingIT {

    private static MockReporter reporter;
    private static ConfigurationRegistry config;
    @LocalServerPort
    private int port;
    private TestRestTemplate restTemplate;

    @BeforeClass
    public static void beforeClass() {
        config = SpyConfiguration.createSpyConfig();
        reporter = new MockReporter();
        ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @Before
    public void setUp() {
        when(config.getConfig(ReporterConfiguration.class).isReportSynchronously()).thenReturn(true);
        restTemplate = new TestRestTemplate(new RestTemplateBuilder()
            .setConnectTimeout(0)
            .setReadTimeout(0));
        reporter.reset();
    }

    @AfterClass
    public static void tearDown() {
        ElasticApmAgent.reset();
    }

    @Test
    public void assertThatErrorCaptured() throws Exception {
        assertThat(restTemplate.getForObject("http://localhost:" + port + "/log-error", String.class))
            .contains("logged");

        // the transaction might not have been reported yet, as the http call returns when the ServletOutputStream has been closed,
        // which is before the transaction has ended
        final Transaction transaction = reporter.getFirstTransaction(500);
        assertThat(transaction.getNameAsString()).isEqualTo("Slf4jErrorCaptureApplication#logError");
        assertThat(reporter.getErrors().size()).isEqualTo(1);
        assertThat(reporter.getErrors().get(0).getException().getMessage()).isEqualTo("some exception");
    }

    @SpringBootApplication
    @RestController
    public static class Slf4jErrorCaptureApplication {

        private static final Logger logger = LoggerFactory.getLogger(Slf4jErrorCaptureApplication.class);

        public static void main(String[] args) {
            SpringApplication.run(Slf4jErrorCaptureApplication.class, args);
        }

        @GetMapping("/log-error")
        public String logError() {
            logger.error("test log error", new RuntimeException("some exception"));
            return "logged";
        }
    }

}
