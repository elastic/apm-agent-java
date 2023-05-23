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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration("src/test/resources")
@Configuration
abstract class AbstractViewRenderingInstrumentationTest extends AbstractInstrumentationTest {


    protected MockMvc mockMvc;

    @Autowired
    WebApplicationContext wac;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    void verifySpanCapture(String spanSubType, String spanSuffix, MockHttpServletResponse response, @Nullable String messageContent) throws UnsupportedEncodingException {
        assertEquals(200, response.getStatus());
        String responseString = response.getContentAsString();
        assertEquals(messageContent, responseString.trim().replaceAll("\r\n", "\n"));
        assertEquals(1, reporter.getSpans().size());
        Span firstSpan = reporter.getSpans().get(0);
        assertEquals("template", firstSpan.getType());
        assertEquals(spanSubType, firstSpan.getSubtype());
        assertEquals("render", firstSpan.getAction());
        assertEquals("View#render" + spanSuffix, firstSpan.getNameAsString());
    }
}
