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
package co.elastic.apm.agent.springwebmvc.template;


import co.elastic.apm.agent.springwebmvc.template.jsp.JspConfiguration;
import co.elastic.apm.agent.springwebmvc.template.jsp.JspController;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.servlet.ModelAndView;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = {JspConfiguration.class, JspController.class})
class Spring5JspViewTest extends AbstractViewRenderingInstrumentationTest {

    @Test
    void testViewRender() throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/jsp"));
        MvcResult mvcResult = resultActions.andReturn();

        verifySpanCapture("InternalResource", " message-view", mvcResult.getResponse(), "");

        ModelAndView modelAndView = mvcResult.getModelAndView();
        assertEquals(modelAndView.getModel().get("message"), "Message 123");
    }
}
