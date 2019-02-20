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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test jax-rs instrumentation with enable_jaxrs_annotation_inheritance=true
 */
public class JaxRsTransactionNameInstrumentationWithEnableJaxrsAnnotationInheritanceTest extends AbstractJaxRsTest {

    private static ElasticApmTracer tracer;
    private static MockReporter reporter;
    private static ConfigurationRegistry config;

    @BeforeClass
    public static void beforeClass() {
        //manual initialization so we can set allow_path_on_hierarchy before instrumentation
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        when(config.getConfig(JaxRsConfiguration.class).isEnableJaxrsAnnotationInheritance()).thenReturn(true);
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @AfterClass
    public static void afterClass() {
        ElasticApmAgent.reset();
    }
    @Test
    public void testJaxRsTransactionNameWithAllowPathInHirarchy() {
        doRequest(tracer, "test");
        doRequest(tracer, "testInterface");
        doRequest(tracer, "testAbstract");
        List<Transaction> actualTransactions = reporter.getTransactions();
        assertThat(actualTransactions).hasSize(3);
        assertThat(actualTransactions.get(0).getName().toString()).isEqualTo("TestResource#testMethod");
        assertThat(actualTransactions.get(1).getName().toString()).isEqualTo("TestResourceWithPathOnInterface#testMethod");
        assertThat(actualTransactions.get(2).getName().toString()).isEqualTo("TestResourceWithPathOnAbstract#testMethod");
    }

}
