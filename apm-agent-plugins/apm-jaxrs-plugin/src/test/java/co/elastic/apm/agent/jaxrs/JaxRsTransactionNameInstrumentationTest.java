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
package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.transaction.TraceContext;
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
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import java.io.IOException;
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
        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("ResourceWithPath#testMethod");
        assertThat(actualTransactions.get(1).getName().toString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(2).getName().toString()).isEqualTo("unnamed");
    }

    @Test
    public void testJaxRsTransactionNameWithJaxrsAnnotationInheritance() {
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");
        doRequest("testInterface");
        doRequest("testAbstract");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("ResourceWithPath#testMethod");
        assertThat(actualTransactions.get(1).getName().toString()).isEqualTo("ResourceWithPathOnInterface#testMethod");
        assertThat(actualTransactions.get(2).getName().toString()).isEqualTo("ResourceWithPathOnAbstract#testMethod");
    }

    @Test
    public void testProxyClassInstrumentationExclusion() {
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("testViewProxy");
        doRequest("testProxyProxy");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(2);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(1).getName().toString()).isEqualTo("unnamed");
    }

    @Test
    public void testJaxRsTransactionNameNonSampledTransactions() throws IOException {
        config.getConfig(CoreConfiguration.class).getSampleRate().update(0.0, SpyConfiguration.CONFIG_SOURCE_NAME);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("ResourceWithPath#testMethod");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabled() {
        when(config.getConfig(CoreConfiguration.class).isUseAnnotationValueForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");
        doRequest("testAbstract");
        doRequest("testInterface");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("GET /test");
        assertThat(actualTransactions.get(1).getName().toString()).isEqualTo("GET /testAbstract");
        assertThat(actualTransactions.get(2).getName().toString()).isEqualTo("GET /testInterface");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceDisabled() {
        when(config.getConfig(CoreConfiguration.class).isUseAnnotationValueForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(false);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");
        doRequest("testInterface");
        doRequest("testAbstract");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("GET /test");
        assertThat(actualTransactions.get(1).getName().toString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(2).getName().toString()).isEqualTo("unnamed");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithPathAnnotation() {
        when(config.getConfig(CoreConfiguration.class).isUseAnnotationValueForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("testWithPathMethod");
        doRequest("testWithPathMethod/15");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(2);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("GET /testWithPathMethod");
        assertThat(actualTransactions.get(1).getName().toString()).isEqualTo("GET /testWithPathMethod/{id}");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithPathAnnotationWithSlash() {
        when(config.getConfig(CoreConfiguration.class).isUseAnnotationValueForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("testWithPathMethodSlash");
        doRequest("testWithPathMethodSlash/15");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(2);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("GET /testWithPathMethodSlash");
        assertThat(actualTransactions.get(1).getName().toString()).isEqualTo("GET /testWithPathMethodSlash/{id}");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithComplexPath() {
        when(config.getConfig(CoreConfiguration.class).isUseAnnotationValueForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("/foo/bar");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("GET /foo/bar");
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
            FooResource.class,
            FooBarResource.class);
    }

    /**
     * Make a GET request against the target path wrapped in an apm transaction.
     *
     * @param path the path to make the get request against
     */
    private void doRequest(String path) {
        final Transaction request = tracer.startTransaction(TraceContext.asRoot(), null, null).withType("request").activate();
        try {
            assertThat(getClient().target(getBaseUri()).path(path).request().buildGet().invoke(String.class)).isEqualTo("ok");
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

    @Path("/foo")
    public static class FooResource {
        @GET
        public String testMethod() {
            return "ok";
        }
    }

    public static class FooBarResource extends FooResource {
        @GET @Path("/bar")
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
        @Path("{id}")
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
