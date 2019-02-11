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
package co.elastic.apm.agent.jaxws;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import static org.assertj.core.api.Java6Assertions.assertThat;

class JaxWsTransactionNameInstrumentationTest extends AbstractInstrumentationTest {

    private HelloWorldService helloWorldService;

    @BeforeEach
    void setUp() {
        helloWorldService = new HelloWorldServiceImpl();
    }

    @Test
    void testTransactionName() {
        final Transaction transaction = tracer.startTransaction();
        try (Scope scope = transaction.activateInScope()) {
            helloWorldService.sayHello();
        } finally {
            transaction.end();
        }
        assertThat(transaction.getName().toString()).isEqualTo("HelloWorldServiceImpl#sayHello");
    }

    @SOAPBinding(style = SOAPBinding.Style.RPC)
    @WebService(targetNamespace = "elastic")
    public interface HelloWorldService {
        @WebMethod
        String sayHello();
    }

    @WebService(serviceName = "HelloWorldService", portName = "HelloWorld", name = "HelloWorld",
        endpointInterface = "co.elastic.apm.agent.jaxws.JaxWsTransactionNameInstrumentationTest.HelloWorldService",
        targetNamespace = "elastic")
    public static class HelloWorldServiceImpl implements HelloWorldService {
        @Override
        public String sayHello() {
            return "Hello World";
        }
    }

}
