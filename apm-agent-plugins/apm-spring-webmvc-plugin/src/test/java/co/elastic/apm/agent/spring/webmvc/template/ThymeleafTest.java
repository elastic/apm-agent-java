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


import co.elastic.apm.agent.spring.webmvc.template.thymeleaf.ThymeleafConfiguration;
import co.elastic.apm.agent.spring.webmvc.template.thymeleaf.ThymeleafController;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = {ThymeleafConfiguration.class, ThymeleafController.class})
class ThymeleafTest extends AbstractViewRenderingInstrumentationTest {

    @Test
    void testViewRender() throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/thymeleaf"));

        MvcResult mvcResult = resultActions.andReturn();

        verifySpanCapture("Thymeleaf", "", mvcResult.getResponse(), "<span>Message 123</span>");
    }
}
