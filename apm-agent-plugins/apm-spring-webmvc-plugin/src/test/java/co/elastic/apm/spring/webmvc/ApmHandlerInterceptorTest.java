/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.spring.webmvc;

import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.servlet.ApmFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class ApmHandlerInterceptorTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        final ElasticApmTracer tracer = ElasticApmTracer.builder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .build();
        this.mockMvc = MockMvcBuilders.standaloneSetup(new TestController(tracer))
            .addFilters(new ApmFilter(tracer))
            .addInterceptors(new ApmHandlerInterceptor(tracer))
            .build();
    }

    @Test
    void getAccount() throws Exception {
        this.mockMvc.perform(get("/test"))
            .andExpect(content().string("TestController#test"));
    }

    @RestController
    public static class TestController {
        private final ElasticApmTracer tracer;

        TestController(ElasticApmTracer tracer) {
            this.tracer = tracer;
        }

        @GetMapping("/test")
        public CharSequence test() {
            final Transaction currentTransaction = tracer.currentTransaction();
            assertThat(currentTransaction).isNotNull();
            return currentTransaction.getName();
        }
    }

}
