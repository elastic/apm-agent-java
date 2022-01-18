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
package co.elastic.apm.agent.springwebmvc;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.ServletWrappingController;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
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
    void prepare() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    }

    @ParameterizedTest(name = "use_path_as_transaction_name = {arguments}")
    @ValueSource(booleans = {false, true})
    void testControllerTransactionName(boolean fallbackToUsePath) throws Exception {

        // User controller name with high priority whenever available
        String expectedName = "TestController#test";
        String urlPath = "/test";

        testTransactionName(fallbackToUsePath, expectedName, expectedName, urlPath);
    }

    @ParameterizedTest(name = "use_path_as_transaction_name = {arguments}")
    @ValueSource(booleans = {false, true})
    void testServletWrappingControllerTransactionName(boolean fallbackToUsePath) throws Exception {

        String expectedContent = "TestServlet";

        // servlet name and method should have higher priority than the controller name
        String expectedName = "SpringTransactionNameInstrumentationTest$TestServlet#doGet";
        String urlPath = "/testServletController";

        testTransactionName(fallbackToUsePath, expectedContent, expectedName, urlPath);
    }

    @ParameterizedTest(name = "use_path_as_transaction_name = {arguments}")
    @ValueSource(booleans = {false, true})
    void testServletWrappingControllerWithoutServletInvocationTransactionName_anonymousController(boolean fallbackToUsePath) throws Exception {

        String expectedContent = "without-servlet-invocation";

        // no servlet invocation through ServletWrappingController, thus name should fall back to the low priority (depending on the use_path_as_transaction_name setting)
        String expectedName = fallbackToUsePath ? "GET /testAnonymousControllerWithoutServlet" : "GET unknown route";
        String urlPath = "/testAnonymousControllerWithoutServlet";

        testTransactionName(fallbackToUsePath, expectedContent, expectedName, urlPath);
    }

    @ParameterizedTest(name = "use_path_as_transaction_name = {arguments}")
    @ValueSource(booleans = {false, true})
    void testServletWrappingControllerWithoutServletInvocationTransactionName_customController(boolean fallbackToUsePath) throws Exception {

        String expectedContent = "without-servlet-invocation";

        // no servlet invocation through ServletWrappingController, thus name should fall back to the low priority (depending on the use_path_as_transaction_name setting)
        String expectedName = fallbackToUsePath ? "GET /testCustomControllerWithoutServlet" : "MyCustomController";
        String urlPath = "/testCustomControllerWithoutServlet";

        testTransactionName(fallbackToUsePath, expectedContent, expectedName, urlPath);
    }

    private void testTransactionName(boolean fallbackToUsePath, String expectedContent, String expectedName, String urlPath) throws Exception {
        doReturn(fallbackToUsePath)
            .when(config.getConfig(WebConfiguration.class))
            .isUsePathAsName();

        mockMvc.perform(get(urlPath))
            .andExpect(content().string(expectedContent));
        assertThat(reporter.getFirstTransaction().getNameAsString())
            .isEqualTo(expectedName);
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
        public ServletWrappingController testAnonymousControllerWithoutServlet () throws Exception {
            ServletWrappingController controller = new ServletWrappingController() {
                @Override
                public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    PrintWriter writer = response.getWriter();
                    writer.write("without-servlet-invocation");
                    return new ModelAndView("anonymous-custom-controller");
                }
            };
            // required, but won't be invoked because the handleRequest method is overriden
            // this can happen with spring static resources controllers which do not delegate execution to servlet
            controller.setServletClass(TestServlet.class);
            controller.setBeanName("testAnonymousControllerWithoutServlet");
            controller.afterPropertiesSet();
            return controller;
        }

        @Bean
        public ServletWrappingController testCustomControllerWithoutServlet () throws Exception {
            ServletWrappingController controller = new MyCustomController();
            // required, but won't be invoked because the handleRequest method is overriden
            // this can happen with spring static resources controllers which do not delegate execution to servlet
            controller.setServletClass(TestServlet.class);
            controller.setBeanName("testCustomControllerWithoutServlet");
            controller.afterPropertiesSet();
            return controller;
        }

        @Bean
        public SimpleUrlHandlerMapping controllerMapping () {
            SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
            Properties urlProperties = new Properties();
            urlProperties.put("/testServletController", "testServletController");
            urlProperties.put("/testAnonymousControllerWithoutServlet", "testAnonymousControllerWithoutServlet");
            urlProperties.put("/testCustomControllerWithoutServlet", "testCustomControllerWithoutServlet");
            mapping.setMappings(urlProperties);
            mapping.setOrder(Integer.MAX_VALUE - 2);
            return mapping;
        }


        @RestController
        public static class TestController {

            @GetMapping("/test")
            public CharSequence test() {
                Transaction currentTransaction = tracer.currentTransaction();
                assertThat(currentTransaction).isNotNull();
                return currentTransaction.getNameAsString();
            }
        }
    }

    private static class MyCustomController extends ServletWrappingController {
        @Override
        public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
            PrintWriter writer = response.getWriter();
            writer.write("without-servlet-invocation");
            return new ModelAndView("anonymous-custom-controller");
        }
    }
}
