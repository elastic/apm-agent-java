/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;

import static org.assertj.core.api.Assertions.assertThat;

public class JaxRsTransactionNameInstrumentationTest extends JerseyTest {

    @BeforeClass
    public static void beforeClass() {
        AbstractInstrumentationTest.beforeAll();
    }

    @AfterClass
    public static void afterClass() {
        AbstractInstrumentationTest.afterAll();
    }

    public Application configure() {
        return new ResourceConfig(TestResource.class);
    }

    @Before
    public void before() {
        AbstractInstrumentationTest.reset();
    }

    @Test
    public void testJaxRsTransactionName() {
        final Transaction request = AbstractInstrumentationTest.getTracer().startTransaction().withType("request").activate();
        try {
            assertThat(getClient().target(getBaseUri()).path("test").request().buildGet().invoke(String.class)).isEqualTo("ok");
        } finally {
            request.deactivate().end();
        }
        assertThat(AbstractInstrumentationTest.getReporter().getFirstTransaction().getName().toString())
            .isEqualTo("TestResource#testMethod");
    }

    public interface SuperResourceInterface {
        @GET
        String testMethod();
    }

    public interface TestResourceInterface extends SuperResourceInterface {
        String testMethod();
    }

    static abstract class AbstractResourceClass implements TestResourceInterface {
    }

    @Path("test")
    public static class TestResource extends AbstractResourceClass {
        public String testMethod() {
            return "ok";
        }
    }
}
