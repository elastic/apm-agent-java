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
package co.elastic.apm.agent.jaxws;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.tracer.Scope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJaxWsInstrumentationTest extends AbstractInstrumentationTest {

    protected BaseHelloWorldService helloWorldService;

    @Test
    void testTransactionName() {
        final Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader());
        try (Scope scope = transaction.activateInScope()) {
            helloWorldService.sayHello();
        } finally {
            transaction.end();
        }
        assertThat(transaction.getNameAsString()).isEqualTo("HelloWorldServiceImpl#sayHello");
        assertThat(transaction.getFrameworkName()).isEqualTo("JAX-WS");
    }

    public interface BaseHelloWorldService {
        String sayHello();
    }
}
