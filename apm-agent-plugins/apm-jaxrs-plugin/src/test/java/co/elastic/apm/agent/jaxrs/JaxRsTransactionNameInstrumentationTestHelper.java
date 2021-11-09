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
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class JaxRsTransactionNameInstrumentationTestHelper {

    public JaxRsTransactionNameInstrumentationTestHelper(ElasticApmTracer tracer, MockReporter reporter, ConfigurationRegistry config, TestObjectPoolFactory objectPoolFactory, Consumer<String> doRequestConsumer) {
        this.tracer = tracer;
        this.reporter = reporter;
        this.config = config;
        this.objectPoolFactory = objectPoolFactory;
        this.doRequestConsumer = doRequestConsumer;
    }

    private ElasticApmTracer tracer;
    private MockReporter reporter;
    private ConfigurationRegistry config;
    private TestObjectPoolFactory objectPoolFactory;
    private Consumer<String> doRequestConsumer;

    public void testJaxRsTransactionNameWithoutJaxrsAnnotationInheritance() {
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(false);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("test");
        doRequestConsumer.accept("testInterface");
        doRequestConsumer.accept("testAbstract");
        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("ResourceWithPath#testMethod");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(2).getNameAsString()).isEqualTo("unnamed");
    }

    public void testJaxRsTransactionNameWithJaxrsAnnotationInheritance() {
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("test");
        doRequestConsumer.accept("testInterface");
        doRequestConsumer.accept("testAbstract");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("ResourceWithPath#testMethod");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("ResourceWithPathOnInterface#testMethod");
        assertThat(actualTransactions.get(2).getNameAsString()).isEqualTo("ResourceWithPathOnAbstract#testMethod");
    }

    public void testJaxRsTransactionNameMethodDelegation() {
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("methodDelegation/methodA");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("MethodDelegationResource#methodA");
    }

    public void testProxyClassInstrumentationExclusion() {
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("testViewProxy");
        doRequestConsumer.accept("testProxyProxy");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(2);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("unnamed");
    }

    public void testJaxRsTransactionNameNonSampledTransactions() throws IOException {
        config.getConfig(CoreConfiguration.class).getSampleRate().update(0.0, SpyConfiguration.CONFIG_SOURCE_NAME);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("test");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("ResourceWithPath#testMethod");
    }

    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabled() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("test");
        doRequestConsumer.accept("testAbstract");
        doRequestConsumer.accept("testInterface");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /test");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("GET /testAbstract");
        assertThat(actualTransactions.get(2).getNameAsString()).isEqualTo("GET /testInterface");
    }

    public void testJaxRsTransactionNameFromPathAnnotationInheritanceDisabled() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(false);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("test");
        doRequestConsumer.accept("testInterface");
        doRequestConsumer.accept("testAbstract");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /test");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("unnamed");
        assertThat(actualTransactions.get(2).getNameAsString()).isEqualTo("unnamed");
    }

    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithPathAnnotation() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("testWithPathMethod");
        doRequestConsumer.accept("testWithPathMethod/15");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(2);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /testWithPathMethod");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("GET /testWithPathMethod/{id}");
    }

    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithPathAnnotationWithSlash() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("testWithPathMethodSlash");
        doRequestConsumer.accept("testWithPathMethodSlash/15");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(2);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /testWithPathMethodSlash");
        assertThat(actualTransactions.get(1).getNameAsString()).isEqualTo("GET /testWithPathMethodSlash/{id}");
    }

    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnMethodWithComplexPath() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("/foo/bar");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /foo/bar");
    }

    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnEmptyPathResource() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /");
    }

    public void testJaxRsTransactionNameFromPathAnnotationInheritanceEnabledOnResourceWithPathAndPathOnInterface() {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("/testInterface/test");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(actualTransactions.get(0).getNameAsString()).isEqualTo("GET /testInterface/test");
    }

    public void testJaxRsFrameworkNameAndVersion(String expectedVersion) throws IOException {
        when(config.getConfig(JaxRsConfiguration.class).isUseJaxRsPathForTransactionName()).thenReturn(true);

        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("test");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("JAX-RS");
        assertThat(reporter.getFirstTransaction().getFrameworkVersion()).isEqualTo(expectedVersion);
    }

    public void testJaxRsFrameworkNameAndVersionWithNonSampledTransaction(String expectedVersion) throws IOException {
        config.getConfig(CoreConfiguration.class).getSampleRate().update(0.0, SpyConfiguration.CONFIG_SOURCE_NAME);
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        doRequestConsumer.accept("test");

        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(1);
        assertThat(reporter.getFirstTransaction().getFrameworkName()).isEqualTo("JAX-RS");
        assertThat(reporter.getFirstTransaction().getFrameworkVersion()).isEqualTo(expectedVersion);
    }
}
