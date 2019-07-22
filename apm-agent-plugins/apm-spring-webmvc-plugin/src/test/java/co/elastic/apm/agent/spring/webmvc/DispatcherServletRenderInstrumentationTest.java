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
package co.elastic.apm.agent.spring.webmvc;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.servlet.ServletInstrumentation;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.handler.ResponseStatusExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@RunWith(value = SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public class DispatcherServletRenderInstrumentationTest {

    private static MockReporter reporter;
    private static ElasticApmTracer tracer;
    private MockMvc mockMvc;

    @BeforeClass
    @BeforeAll
    public static void setUpAll() {
        reporter = new MockReporter();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(),
            Arrays.asList(new ServletInstrumentation(tracer), new DispatcherServletRenderInstrumentation()));
    }

    @AfterClass
    @AfterAll
    public static void afterAll() {
        ElasticApmAgent.reset();
    }

    @Before
    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
            .standaloneSetup(new MessageController(), new ExceptionService())
            .setControllerAdvice(new ApplicationExceptionHandler())
            .setViewResolvers(jspViewResolver())
            .build();
    }

    private ViewResolver jspViewResolver() {
        InternalResourceViewResolver bean = new InternalResourceViewResolver();
        bean.setPrefix("/static/");
        bean.setSuffix(".jsp");
        return bean;
    }

    @Controller
    public static class MessageController {

        @Autowired
        private ExceptionService exceptionService;

        @GetMapping("/test")
        public ModelAndView test() {
            ModelAndView modelAndView = new ModelAndView();
            modelAndView.setViewName("message-view");
            return modelAndView;
        }

        @GetMapping("/throw-exception")
        public ResponseEntity throwException() {
            exceptionService.throwException();
            return new ResponseEntity("OK", HttpStatus.OK);
        }
    }

    @Service
    public static class ExceptionService {

        public void throwException() {
            throw new RuntimeException("Ba-ba-bah");
        }
    }

    @ControllerAdvice
    public static class ApplicationExceptionHandler extends ResponseStatusExceptionHandler {

        @ExceptionHandler(Exception.class)
        public ResponseEntity<?> globalExceptionHandler(Exception ex) {
            System.out.println("Catch exception");
            return new ResponseEntity<>("Global exception: " + ex.getMessage(), HttpStatus.CONFLICT);
        }
    }

    @Test
    public void testCallOfDispatcherServletWithNonNullModelAndView() throws Exception {
        reporter.reset();
        this.mockMvc.perform(get("/test"));
        assertEquals(1, reporter.getTransactions().size());
        assertEquals(1, reporter.getSpans().size());
        assertEquals("DispatcherServlet#render message-view", reporter.getSpans().get(0).getName().toString());
    }

    @Test
    public void testCallApiWithExceptionThrown() throws Exception {
        reporter.reset();;
        this.mockMvc.perform(get("/throw-exception"));
    }
}
