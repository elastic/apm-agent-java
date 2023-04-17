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
package co.elastic.apm.agent.springwebmvc.exception;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.springwebmvc.exception.testapp.exception_handler.ExceptionHandlerController;
import co.elastic.apm.agent.springwebmvc.exception.testapp.exception_handler.ExceptionHandlerRuntimeException;
import co.elastic.apm.agent.springwebmvc.exception.testapp.exception_handler.ExceptionHandlerRuntimeException200;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = {
    ExceptionHandlerController.class})
public class Spring5ExceptionHandlerInstrumentationWithExceptionHandlerTest extends AbstractExceptionHandlerInstrumentationTest {

    @Test
    public void testExceptionCapture() throws Exception {
        ResultActions resultActions = this.mockMvc.perform(get("/exception-handler/throw-exception"));
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse response = result.getResponse();

        assertExceptionCapture(ExceptionHandlerRuntimeException.class, response, 409, "exception-handler runtime exception occurred", "runtime exception occurred");
    }

    @Test
    public void testExceptionCapture_IgnoreException() throws Exception {
        doReturn(List.of(WildcardMatcher.valueOf("co.elastic.apm.agent.springwebmvc.exception.testapp.exception_handler.ExceptionHandlerRuntimeException")))
            .when(config.getConfig(CoreConfiguration.class)).getIgnoreExceptions();

        this.mockMvc.perform(get("/exception-handler/throw-exception"));

        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getErrors()).isEmpty();
    }

    @Test
    public void testExceptionCapture_StatusCode200() throws Exception {
        ResultActions resultActions = this.mockMvc.perform(get("/exception-handler/throw-exception-sc200"));
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse response = result.getResponse();

        assertExceptionCapture(ExceptionHandlerRuntimeException200.class, response, 200, "exception-handler runtime exception occurred", "runtime exception occurred");
    }
}
