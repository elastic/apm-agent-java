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
package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class JaxRsTransactionNameInstrumentationTest extends JerseyTest {

    private ElasticApmTracer tracer;
    private MockReporter reporter;
    private ConfigurationRegistry config;
    private TestObjectPoolFactory objectPoolFactory;

    private JaxRsTransactionNameInstrumentationTestHelper helper;

    @Before
    public void before() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        reporter = mockInstrumentationSetup.getReporter();
        config = mockInstrumentationSetup.getConfig();
        tracer = mockInstrumentationSetup.getTracer();
        objectPoolFactory = mockInstrumentationSetup.getObjectPoolFactory();
        helper = new JaxRsTransactionNameInstrumentationTestHelper(tracer, reporter, config, objectPoolFactory, this::doRequest);
    }

    @After
    public void after() {
        try {
            reporter.assertRecycledAfterDecrementingReferences();
            objectPoolFactory.checkAllPooledObjectsHaveBeenRecycled();
        } finally {
            reporter.reset();
            objectPoolFactory.reset();
            ElasticApmAgent.reset();
        }
    }

    @Test
    public void testJaxRsTransactionNameWithoutJaxrsAnnotationInheritance() {
        helper.testJaxRsTransactionNameWithoutJaxrsAnnotationInheritance();
    }

    @Test
    public void testJaxRsTransactionNameWithJaxrsAnnotationInheritance() {
        helper.testJaxRsTransactionNameWithJaxrsAnnotationInheritance();
    }

    @Test
    public void testJaxRsTransactionNameMethodDelegation() {
        helper.testJaxRsTransactionNameMethodDelegation();
    }

    @Test
    public void testProxyClassInstrumentationExclusion() {
        helper.testProxyClassInstrumentationExclusion();
    }

    @Test
    public void testJaxRsTransactionNameNonSampledTransactions() throws IOException {
        helper.testJaxRsTransactionNameNonSampledTransactions();
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabled() {
        helper.testJaxRsTransactionNameFromPathAnnotationInheritanceEnabled();
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceDisabled() {
        helper.testJaxRsTransactionNameFromPathAnnotationInheritanceDisabled();
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithPathAnnotation() {
        helper.testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithPathAnnotation();
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithPathAnnotationWithSlash() {
        helper.testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithPathAnnotationWithSlash();
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithComplexPath() {
        helper.testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithComplexPath();
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnEmptyPathResource() {
        helper.testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnEmptyPathResource();
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnResourceWithPathAndPathOnInterface() {
        helper.testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnResourceWithPathAndPathOnInterface();
    }

    @Test
    public void testJaxRsFrameworkNameAndVersion() throws IOException {
        helper.testJaxRsFrameworkNameAndVersion("2.1");
    }

    @Test
    public void testJaxRsFrameworkNameAndVersionWithNonSampledTransaction() throws IOException {
        helper.testJaxRsFrameworkNameAndVersionWithNonSampledTransaction("2.1");
    }

    /**
     * Make a GET request against the target path wrapped in an apm transaction.
     *
     * @param path the path to make the get request against
     */
    private void doRequest(String path) {
        final Transaction request = tracer.startRootTransaction(null)
            .withType("request")
            .activate();
        try {
            assertThat(getClient().target(getBaseUri()).path(path).request().buildGet().invoke(String.class)).isEqualTo("ok");
        } finally {
            request
                .deactivate()
                .end();
        }
    }

    /**
     * @return configuration for the jersey test server. Includes all resource classes in the co.elastic.apm.agent.jaxrs.resources package.
     */
    @Override
    protected Application configure() {
        return new ResourceConfig(
            ResourceWithPath.class,
            ResourceWithPathOnInterface.class,
            ResourceWithPathOnAbstract.class,
            ProxiedClass$$$view.class,
            ProxiedClass$Proxy.class,
            ResourceWithPathOnMethod.class,
            ResourceWithPathOnMethodSlash.class,
            MethodDelegationResource.class,
            FooBarResource.class,
            EmptyPathResource.class,
            ResourceWithPathAndWithPathOnInterface.class);
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
    }

    @Path("testViewProxy")
    public static class ProxiedClass$$$view implements SuperResourceInterface {
        public String testMethod() {
            return "ok";
        }
    }

    @Path("testProxyProxy")
    public static class ProxiedClass$Proxy implements SuperResourceInterface {
        public String testMethod() {
            return "ok";
        }
    }

    @Path("test")
    public static class ResourceWithPath extends AbstractResourceClassWithoutPath {
        public String testMethod() {
            return "ok";
        }
    }

    @Path("methodDelegation")
    public static class MethodDelegationResource {
        @GET
        @Path("methodA")
        public String methodA() {
            methodB();
            return "ok";
        }

        @POST
        public void methodB() {
        }
    }

    @Path("/foo/")
    public static class FooResource {
        @GET
        @Path("/ignore")
        public String testMethod() {
            return "ok";
        }
    }

    public static class FooBarResource extends FooResource {
        @GET
        @Path("/bar")
        @Override
        public String testMethod() {
            return "ok";
        }
    }

    @Path("testWithPathMethod")
    public static class ResourceWithPathOnMethod extends AbstractResourceClassWithoutPath {

        @Override
        public String testMethod() {
            return "ok";
        }

        @GET
        @Path("{id}/")
        public String testMethodById(@PathParam("id") String id) {
            return "ok";
        }
    }

    @Path("testWithPathMethodSlash")
    public static class ResourceWithPathOnMethodSlash extends AbstractResourceClassWithoutPath {

        @Override
        public String testMethod() {
            return "ok";
        }

        @GET
        @Path("/{id}")
        public String testMethodById(@PathParam("id") String id) {
            return "ok";
        }
    }

    @Path("")
    public static class EmptyPathResource {
        @GET
        public String testMethod() {
            return "ok";
        }
    }

    public static class ResourceWithPathAndWithPathOnInterface implements ResourceInterfaceWithPath {
        @Override
        @GET
        @Path("test")
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
