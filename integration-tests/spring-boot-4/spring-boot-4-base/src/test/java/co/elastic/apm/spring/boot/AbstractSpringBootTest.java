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
package co.elastic.apm.spring.boot;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.report.ReporterConfigurationImpl;
import co.elastic.apm.agent.tracer.configuration.WebConfiguration;
import co.elastic.apm.api.ElasticApm;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public abstract class AbstractSpringBootTest {

    private static MockReporter reporter;
    private static ConfigurationRegistry config;
    @LocalServerPort
    private int port;
    private TestRestTemplate restTemplate;

    @BeforeAll
    public static void beforeClass() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        config = mockInstrumentationSetup.getConfig();
        reporter = mockInstrumentationSetup.getReporter();
        ElasticApmAgent.initInstrumentation(mockInstrumentationSetup.getTracer(), ByteBuddyAgent.install());
    }

    @AfterAll
    public static void tearDown() {
        ElasticApmAgent.reset();
    }

    @BeforeEach
    public void setUp() {
        doReturn(true).when(config.getConfig(ReporterConfigurationImpl.class)).isReportSynchronously();
        restTemplate = new TestRestTemplate(new RestTemplateBuilder()
                                                .connectTimeout(Duration.ofSeconds(10))
                                                .readTimeout(Duration.ofSeconds(10))
                                                .basicAuthentication("username", "password"));
        reporter.reset();
    }

    @Test
    public void greetingShouldReturnDefaultMessage() throws Exception {
        assertThat(restTemplate.getForObject("http://localhost:" + port + "/", String.class))
            .contains("Hello World");

        // the transaction might not have been reported yet, as the http call returns when the ServletOutputStream has been closed,
        // which is before the transaction has ended
        final TransactionImpl transaction = reporter.getFirstTransaction(500);
        assertThat(transaction.getNameAsString()).isEqualTo("TestApp#greeting");
        assertThat(transaction.getContext().getUser().getDomain()).isEqualTo("domain");
        assertThat(transaction.getContext().getUser().getId()).isEqualTo("id");
        assertThat(transaction.getContext().getUser().getEmail()).isEqualTo("email");
        assertThat(transaction.getContext().getUser().getUsername()).isEqualTo("username");
        // as this test runs in a standalone application and not in a servlet container,
        // the service.name will not be overwritten for the webapp class loader based on spring.application.name
        assertThat(transaction.getTraceContext().getServiceName()).isNull();
        assertThat(transaction.getFrameworkName()).isEqualTo("Spring Web MVC");
        assertThat(transaction.getFrameworkVersion()).matches(getExpectedSpringVersionRegex());
    }

    protected String getExpectedSpringVersionRegex() {
        return "5\\.[0-9]+\\.[0-9]+";
    }

    @Test
    public void testStaticFile() {
        doReturn(Collections.emptyList()).when(config.getConfig(WebConfiguration.class)).getIgnoreUrls();
        assertThat(restTemplate.getForObject("http://localhost:" + port + "/script.js", String.class))
            .contains("// empty test script");

        assertThat(reporter.getFirstTransaction(500).getNameAsString()).isEqualTo("ResourceHttpRequestHandler");
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("Spring Web MVC");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getUsername()).isEqualTo("username");
    }

    @RestController
    @SpringBootApplication
    @EnableWebSecurity
    public static class TestApp {

        public static void main(String[] args) {
            SpringApplication.run(TestApp.class, args);
        }

        @GetMapping("/")
        public String greeting() {
            ElasticApm.currentTransaction().setUser("id", "email", "username", "domain");
            return "Hello World";
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) {
            return http.authorizeHttpRequests(registry -> registry.anyRequest().authenticated())
                .httpBasic(registry -> {})
                .build();
        }

        @Bean
        public UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(User.withDefaultPasswordEncoder()
                                                      .username("username")
                                                      .password("password")
                                                      .roles("USER")
                                                      .build());
        }

    }
}
