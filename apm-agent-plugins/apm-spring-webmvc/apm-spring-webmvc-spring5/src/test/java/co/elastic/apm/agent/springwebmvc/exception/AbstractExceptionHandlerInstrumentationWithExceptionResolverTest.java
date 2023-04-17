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

import co.elastic.apm.agent.springwebmvc.exception.testapp.exception_resolver.ExceptionResolverController;
import co.elastic.apm.agent.springwebmvc.exception.testapp.exception_resolver.ExceptionResolverRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Abstract to be agnostic of javax.servlet / jakarta.servlet for spring 5 / spring 6.
 * Implementations need to add the correc implementation for {@link  co.elastic.apm.agent.springwebmvc.exception.testapp.exception_resolver.AbstractRestResponseStatusExceptionResolver}
 * to the context.
 */
@ContextConfiguration(classes = {
    ExceptionResolverController.class
})
public abstract class AbstractExceptionHandlerInstrumentationWithExceptionResolverTest extends AbstractExceptionHandlerInstrumentationTest {

    @Test
    public void testCallApiWithExceptionThrown() throws Exception {
        ResultActions resultActions = this.mockMvc.perform(get("/exception-resolver/throw-exception"));
        MvcResult result = resultActions.andReturn();
        MockHttpServletResponse response = result.getResponse();

        assertExceptionCapture(ExceptionResolverRuntimeException.class, response, 200, "", "runtime exception occurred", "View#render error-page");
        assertEquals("error-page", response.getForwardedUrl());
        assertEquals("runtime exception occurred", result.getModelAndView().getModel().get("message"));
    }
}
