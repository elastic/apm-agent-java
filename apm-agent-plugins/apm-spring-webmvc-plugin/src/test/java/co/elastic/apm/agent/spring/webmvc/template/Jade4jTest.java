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
package co.elastic.apm.agent.spring.webmvc.template;


import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.spring.webmvc.template.jade4j.Jade4jController;
import co.elastic.apm.agent.spring.webmvc.template.jade4j.Jade4jTemplateConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = {Jade4jTemplateConfiguration.class, Jade4jController.class})
class Jade4jTest extends AbstractViewRenderingInstrumentationTest {

    @Test
    void testExceptionCapture() throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/jade4j"));

        MvcResult mvcResult = resultActions.andReturn();
        MockHttpServletResponse response = mvcResult.getResponse();
        assertEquals(200, response.getStatus());
        String responseString = mvcResult.getResponse().getContentAsString();
        assertEquals("<!DOCTYPE html><html><body><Hello>Message 123</Hello></body></html>", responseString.trim());
        assertEquals(1, reporter.getSpans().size());
        Span firstSpan = reporter.getSpans().get(0);
        assertEquals("template", firstSpan.getType());
        assertEquals("Jade", firstSpan.getSubtype());
        assertEquals("render", firstSpan.getAction());
        assertEquals("DispatcherServlet#render hello", firstSpan.getNameAsString());
    }
}
