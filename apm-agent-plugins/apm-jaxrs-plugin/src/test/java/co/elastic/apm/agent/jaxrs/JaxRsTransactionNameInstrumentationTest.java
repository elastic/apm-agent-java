/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test jax-rs instrumentation with allow_path_on_hierarchy=false
 */
public class JaxRsTransactionNameInstrumentationTest extends AbstractJaxRsTest {

    @BeforeClass
    public static void beforeClass() {
        AbstractInstrumentationTest.beforeAll();
    }

    @AfterClass
    public static void afterClass() {
        AbstractInstrumentationTest.afterAll();
    }


    @Before
    public void before() {
        AbstractInstrumentationTest.reset();
    }

    @Test
    public void testJaxRsTransactionName() {
        doRequest(AbstractInstrumentationTest.getTracer(), "test");
        doRequest(AbstractInstrumentationTest.getTracer(), "testInterface");
        doRequest(AbstractInstrumentationTest.getTracer(), "testAbstract");
        List<Transaction> actualTransactions = AbstractInstrumentationTest.getReporter().getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("TestResource#testMethod");
        assertThat(actualTransactions.get(1).getName().toString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(2).getName().toString()).isEqualTo("unnamed");
    }

}
