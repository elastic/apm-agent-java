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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.ServletWrappingController;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.Function;


public class Spring6TransactionNameInstrumentationTest extends Java17OnlyTest {

    public Spring6TransactionNameInstrumentationTest() {
        super(Impl.class);
    }

    public static class Impl extends AbstractSpringTransactionNameInstrumentationTest {
        public static class TestServlet extends HttpServlet {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                resp.getWriter().print("TestServlet");
            }
        }

        @Configuration
        public static class TestConfiguration extends AbstractTestConfiguration {

            @Override
            protected Class<?> getTestServletClass() {
                return TestServlet.class;
            }

            @Override
            protected void configureTestServletOnController(ServletWrappingController controller) {
                controller.setServletClass(TestServlet.class);
            }

            @Override
            protected ServletWrappingController createMyCustomController(Function<PrintWriter, ModelAndView> handler) {
                return new MyCustomController(handler);
            }

            @Override
            protected ServletWrappingController createAnonymousController(Function<PrintWriter, ModelAndView> handler) {
                return new ServletWrappingController() {
                    @Override
                    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
                        return handler.apply(response.getWriter());
                    }
                };
            }
        }


        private static class MyCustomController extends ServletWrappingController {

            private final Function<PrintWriter, ModelAndView> handler;

            public MyCustomController(Function<PrintWriter, ModelAndView> handler) {
                this.handler = handler;
            }

            @Override
            public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
                return handler.apply(response.getWriter());
            }
        }
    }
}
