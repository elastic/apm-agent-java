/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.spring.boot;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.web.WebConfiguration;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class SpringBoot1_5IT {

    private MockReporter reporter;
    private ConfigurationRegistry config;
    private TestRestTemplate restTemplate;
    @LocalServerPort
    private int port;

    @Before
    public void setUp() {
        config = SpyConfiguration.createSpyConfig();
        when(config.getConfig(ReporterConfiguration.class).isReportSynchronously()).thenReturn(true);
        reporter = new MockReporter();
        ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
        restTemplate = new TestRestTemplate(new RestTemplateBuilder().setConnectTimeout(0).setReadTimeout(0));
        reporter.reset();
    }

    @After
    public void tearDown() {
        ElasticApmAgent.reset();
    }

    @Test
    public void greetingShouldReturnDefaultMessage() throws Exception {
        assertThat(restTemplate.getForObject("http://localhost:" + port + "/", String.class)).contains("Hello World");

        // the transaction might not have been reported yet, as the http call returns when the ServletOutputStream has been closed,
        // which is before the transaction has ended
        assertThat(reporter.getFirstTransaction(500).getName().toString()).isEqualTo("TestApp#greeting");
    }

    @Test
    public void testStaticFile() throws Exception {
        when(config.getConfig(WebConfiguration.class).getIgnoreUrls()).thenReturn(Collections.emptyList());
        assertThat(restTemplate.getForObject("http://localhost:" + port + "/script.js", String.class))
            .contains("// empty test script");

        assertThat(reporter.getFirstTransaction(500).getName().toString()).isEqualTo("ResourceHttpRequestHandler");
    }

    @Controller
    @SpringBootApplication
    public static class TestApp {

        public static void main(String[] args) {
            SpringApplication.run(TestApp.class, args);
        }

        @ResponseBody
        @RequestMapping("/")
        public String greeting() {
            return "Hello World";
        }
    }
}
