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
package co.elastic.apm.agent.spring.webmvc.exception;

import co.elastic.apm.agent.spring.webmvc.exception.testapp.controller_advice.ControllerAdviceController;
import co.elastic.apm.agent.spring.webmvc.exception.testapp.controller_advice.ControllerAdviceRuntimeException;
import co.elastic.apm.agent.spring.webmvc.exception.testapp.controller_advice.GlobalExceptionHandler;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = {
    ControllerAdviceController.class,
    GlobalExceptionHandler.class
})
public class ExceptionHandlerInstrumentationWithGlobalAdviceTest extends AbstractExceptionHandlerInstrumentationTest {

    @Test
    public void testExceptionCaptureWithGlobalControllerAdvice() throws Exception {

        ResultActions resultActions = this.mockMvc.perform(get("/controller-advice/throw-exception"));
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse response = result.getResponse();

        assertExceptionCapture(ControllerAdviceRuntimeException.class, response, 409, "controller-advice runtime exception occured", "runtime exception occured");
    }
}
