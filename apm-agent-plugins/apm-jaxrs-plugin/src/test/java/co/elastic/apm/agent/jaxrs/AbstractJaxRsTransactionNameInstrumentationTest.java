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
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import net.bytebuddy.agent.ByteBuddyAgent;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test jax-rs instrumentation
 */
public abstract class AbstractJaxRsTransactionNameInstrumentationTest extends JerseyTest {

    private ElasticApmTracer tracer;
    private MockReporter reporter;
    private ConfigurationRegistry config;
    private TestObjectPoolFactory objectPoolFactory;

    @Before
    public void before() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        reporter = mockInstrumentationSetup.getReporter();
        config = mockInstrumentationSetup.getConfig();
        tracer = mockInstrumentationSetup.getTracer();
        objectPoolFactory = mockInstrumentationSetup.getObjectPoolFactory();
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
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(false);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");
        doRequest("testInterface");
        doRequest("testAbstract");
        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("ResourceWithPath#testMethod");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(2).getNameAsString()).isEqualTo("unnamed");
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
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("ResourceWithPath#testMethod");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("ResourceWithPathOnInterface#testMethod");
        assertThat(actualTransactions.get(2).getNameAsString()).isEqualTo("ResourceWithPathOnAbstract#testMethod");
    }

    @Test
    public void testJaxRsTransactionNameMethodDelegation() {
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("methodDelegation/methodA");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("MethodDelegationResource#methodA");
    }

    @Test
    public void testProxyClassInstrumentationExclusion() {
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("testViewProxy");
        doRequest("testProxyProxy");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(2);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("unnamed");
    }

    @Test
    public void testJaxRsTransactionNameNonSampledTransactions() throws IOException {
        config.getConfig(CoreConfiguration.class).getSampleRate().update(0.0, SpyConfiguration.CONFIG_SOURCE_NAME);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("ResourceWithPath#testMethod");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabled() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");
        doRequest("testAbstract");
        doRequest("testInterface");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /test");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("GET /testAbstract");
        assertThat(actualTransactions.get(2).getNameAsString()).isEqualTo("GET /testInterface");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceDisabled() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(false);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");
        doRequest("testInterface");
        doRequest("testAbstract");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /test");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(2).getNameAsString()).isEqualTo("unnamed");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithPathAnnotation() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("testWithPathMethod");
        doRequest("testWithPathMethod/15");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(2);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /testWithPathMethod");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("GET /testWithPathMethod/{id}");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithPathAnnotationWithSlash() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("testWithPathMethodSlash");
        doRequest("testWithPathMethodSlash/15");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(2);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /testWithPathMethodSlash");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("GET /testWithPathMethodSlash/{id}");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithComplexPath() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("/foo/bar");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /foo/bar");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnEmptyPathResource() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /");
    }

    @Test
    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnResourceWithPathAndPathOnInterface() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("/testInterface/test");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /testInterface/test");
    }

    @Test
    public void testJaxRsFrameworkNameAndVersion() throws IOException {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("JAX-RS");
        assertThat(reporter.getFirstTransaction().getFrameworkVersion()).isEqualTo("2.1");
    }

    @Test
    public void testJaxRsFrameworkNameAndVersionWithNonSampledTransaction() throws IOException {
        config.getConfig(CoreConfiguration.class).getSampleRate().update(0.0, SpyConfiguration.CONFIG_SOURCE_NAME);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequest("test");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("JAX-RS");
        assertThat(reporter.getFirstTransaction().getFrameworkVersion()).isEqualTo("2.1");
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
            assertThat(super.getClient().target(getBaseUri()).path(path).request().buildGet().invoke(String.class)).isEqualTo("ok");
        } finally {
            request
                .deactivate()
                .end();
        }
    }

}
