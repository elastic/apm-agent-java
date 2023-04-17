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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.UnsupportedEncodingException;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;


@ExtendWith(SpringExtension.class)
@WebAppConfiguration
public abstract class AbstractExceptionHandlerInstrumentationTest extends AbstractInstrumentationTest {

    protected MockMvc mockMvc;

    @Autowired
    WebApplicationContext wac;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    protected void assertExceptionCapture(Class exceptionClazz, MockHttpServletResponse response, int statusCode, String responseContent, String exceptionMessageContains) throws UnsupportedEncodingException {
        assertExceptionCapture(exceptionClazz, response, statusCode, responseContent, exceptionMessageContains, null);
    }

    protected void assertExceptionCapture(Class exceptionClazz, MockHttpServletResponse response, int statusCode, String responseContent, String exceptionMessageContains, String childSpanNameContains) throws UnsupportedEncodingException {

        assertThat(reporter.getTransactions()).hasSize(1);
        Transaction transaction = reporter.getFirstTransaction();
        if (childSpanNameContains == null) {
            assertThat(reporter.getSpans()).isEmpty();
        } else {
            assertThat(reporter.getSpans()).hasSize(1);
            assertThat(reporter.getFirstSpan())
                .hasParent(transaction)
                .hasNameContaining(childSpanNameContains);
        }

        assertThat(reporter.getErrors())
            .hasSize(1)
            .first().satisfies(error -> {
                assertThat(error.getException())
                    .hasMessageContaining(exceptionMessageContains)
                    .isInstanceOf(exceptionClazz);
            });
        assertThat(response.getStatus()).isEqualTo(statusCode);
        assertThat(transaction.getContext().getResponse().getStatusCode()).isEqualTo(statusCode);
        assertThat(response.getContentAsString()).isEqualTo(responseContent);
    }

}
