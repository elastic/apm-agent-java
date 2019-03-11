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

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test jax-rs instrumentation
 */
public class JaxRsTransactionNameInstrumentationTest extends JerseyTest {

    private static ElasticApmTracer tracer;
    private static MockReporter reporter;
    private static ConfigurationRegistry config;

    @BeforeClass
    public static void beforeClass() {
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
    }

    @After
    public void after() {
        //reset after each method to test different non-dynamic parameters
        ElasticApmAgent.reset();
    }

    @Before
    public void before() {
        SpyConfiguration.reset(config);
        reporter.reset();
    }

    @Test
    public void testJaxRsTransactionNameWithoutJaxrsAnnotationInheritance() {
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(false);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");
        doRequest("testInterface");
        doRequest("testAbstract");
        doRequest("POST", "testAbstract");
        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(4);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("ResourceWithPath#testMethod");
        assertThat(actualTransactions.get(1).getName().toString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(2).getName().toString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(3).getName().toString()).isEqualTo("AbstractResourceClassWithPath#testMethodOnAbstractClass");
    }

    @Test
    public void testJaxRsTransactionNameWithJaxrsAnnotationInheritance() {
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");
        doRequest("testInterface");
        doRequest("testAbstract");
        doRequest("POST", "testAbstract");
        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(4);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("ResourceWithPath#testMethod");
        assertThat(actualTransactions.get(1).getName().toString()).isEqualTo("ResourceWithPathOnInterface#testMethod");
        assertThat(actualTransactions.get(2).getName().toString()).isEqualTo("ResourceWithPathOnAbstract#testMethod");
        assertThat(actualTransactions.get(3).getName().toString()).isEqualTo("AbstractResourceClassWithPath#testMethodOnAbstractClass");
    }


    /**
     * @return configuration for the jersey test server. Includes all resource classes in the co.elastic.apm.agent.jaxrs.resources package.
     */
    protected Application configure() {
        return new ResourceConfig(ResourceWithPath.class, ResourceWithPathOnInterface.class, ResourceWithPathOnAbstract.class);
    }

    /**
     * Make a GET request against the target path wrapped in an apm transaction.
     *
     * @param path the path to make the get request against
     */
    private void doRequest(String path) {
        doRequest("GET", path);
    }

    /**
     * Make a GET request against the target path wrapped in an apm transaction.
     *
     * @param method
     * @param path the path to make the get request against
     */
    private void doRequest(String method, String path) {
        final Transaction request = tracer.startTransaction().withType("request").activate();
        try {
            assertThat(getClient().target(getBaseUri()).path(path).request().build(method).invoke(String.class)).isEqualTo("ok");
        } finally {
            request.deactivate().end();
        }
    }

    public interface SuperResourceInterface {
        @GET
        String testMethod();
    }

    @Path("testInterface")
    public interface ResourceInterfaceWithPath extends SuperResourceInterface {
        String testMethod();

    }

    public interface ResourceInterfaceWithoutPath extends SuperResourceInterface {
        String testMethod();
    }

    public abstract static class AbstractResourceClassWithoutPath implements ResourceInterfaceWithoutPath {

    }

    @Path("testAbstract")
    public abstract static class AbstractResourceClassWithPath implements ResourceInterfaceWithoutPath {
        @POST
        public String testMethodOnAbstractClass() {
            return "ok";
        }
    }

    @Path("test")
    public static class ResourceWithPath extends AbstractResourceClassWithoutPath {
        public String testMethod() {
            return "ok";
        }

    }

    public static class ResourceWithPathOnAbstract extends AbstractResourceClassWithPath {
        public String testMethod() {
            return "ok";
        }
    }

    public static class ResourceWithPathOnInterface implements ResourceInterfaceWithPath {
        public String testMethod() {
            return "ok";
        }
    }
    
}
