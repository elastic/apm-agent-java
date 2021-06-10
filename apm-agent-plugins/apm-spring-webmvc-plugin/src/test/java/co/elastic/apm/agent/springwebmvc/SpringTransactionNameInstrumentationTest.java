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
package co.elastic.apm.agent.springwebmvc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.ServletWrappingController;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableWebMvc
class SpringTransactionNameInstrumentationTest extends AbstractInstrumentationTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void prepare() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    }

    @Test
    void testControllerTransactionName() throws Exception {
        mockMvc.perform(get("/test"))
            .andExpect(content().string("TestController#test"));
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("TestController#test");
    }

    @Test
    void testServletWrappingControllerTransactionName() throws Exception {
        mockMvc.perform(get("/testServletController"))
            .andExpect(content().string("TestServlet"));
        assertThat(reporter.getFirstTransaction().getNameAsString()).isEqualTo("SpringTransactionNameInstrumentationTest$TestServlet#doGet");
    }

    public static class TestServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.getWriter().print("TestServlet");
        }
    }

    @Configuration
    public static class TestConfiguration {

        @Bean
        public ServletWrappingController testServletController () throws Exception {
            ServletWrappingController controller = new ServletWrappingController();
            controller.setServletClass(TestServlet.class);
            controller.setBeanName("testServletController");
            controller.afterPropertiesSet();
            return controller;
        }

        @Bean
        public SimpleUrlHandlerMapping controllerMapping () {
            SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
            Properties urlProperties = new Properties();
            urlProperties.put("/testServletController", "testServletController");
            mapping.setMappings(urlProperties);
            mapping.setOrder(Integer.MAX_VALUE - 2);

            return mapping;
        }


        @RestController
        public static class TestController {

            @GetMapping("/test")
            public CharSequence test() {
                final Transaction currentTransaction = tracer.currentTransaction();
                assertThat(currentTransaction).isNotNull();
                return currentTransaction.getNameAsString();
            }
        }
    }

}
