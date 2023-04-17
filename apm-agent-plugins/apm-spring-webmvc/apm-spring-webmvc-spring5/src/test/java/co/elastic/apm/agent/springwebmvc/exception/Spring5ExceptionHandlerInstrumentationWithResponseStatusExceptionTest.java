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

import co.elastic.apm.agent.springwebmvc.exception.testapp.response_status_exception.ResponseStatusExceptionController;
import co.elastic.apm.agent.springwebmvc.exception.testapp.response_status_exception.ResponseStatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = {
    ResponseStatusExceptionController.class})
public class Spring5ExceptionHandlerInstrumentationWithResponseStatusExceptionTest extends AbstractExceptionHandlerInstrumentationTest {

    @Test
    public void testCallApiWithExceptionThrown() throws Exception {

        ResultActions resultActions = this.mockMvc.perform(get("/response-status-exception/throw-exception"));
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse response = result.getResponse();

        assertExceptionCapture(ResponseStatusException.class, response, 409, "", "409 CONFLICT \"responseStatusException\"");
        assertEquals("runtime exception occurred", reporter.getErrors().get(0).getException().getCause().getMessage());
        assertEquals(ResponseStatusRuntimeException.class, reporter.getErrors().get(0).getException().getCause().getClass());
    }
}
