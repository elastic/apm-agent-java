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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.metadata.MetaData;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.report.ApmServerClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;


import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DropUnsampledTransactionsTest extends AbstractInstrumentationTest {

    private static ApmServerClient apmServerClient = mock(ApmServerClient.class);

    private static MockReporter reporter = new MockReporter();

    private static ElasticApmTracer tracer;

    @BeforeAll
    static void startTracer() {
        ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        tracer = new ElasticApmTracer(configurationRegistry, reporter, new TestObjectPoolFactory(), apmServerClient, "ephemeralId", MetaData.create(configurationRegistry, "ephemeralId"));
        tracer.start(false);
    }

    @AfterAll
    static void stopTracer() {
        tracer.stop();
    }

    @AfterEach
    void resetReporter() {
        reporter.reset();
    }

    @Test
    void whenTheAPMServerSupportsKeepingUnsampledTransactionsUnsampledTransactionsShouldBeReported() throws IOException {
        when(apmServerClient.supportsKeepingUnsampledTransaction()).thenReturn(true);
        tracer.getConfig(CoreConfiguration.class).getSampleRate().update(0.0, SpyConfiguration.CONFIG_SOURCE_NAME);

        tracer.startRootTransaction(null).end();

        assertThat(reporter.getTransactions().size()).isEqualTo(1);
    }

    @Test
    void whenTheAPMServerSupportsKeepingUnsampledTransactionsSampledTransactionsShouldBeReported() throws IOException {
        when(apmServerClient.supportsKeepingUnsampledTransaction()).thenReturn(true);
        tracer.getConfig(CoreConfiguration.class).getSampleRate().update(1.0, SpyConfiguration.CONFIG_SOURCE_NAME);

        tracer.startRootTransaction(null).end();

        assertThat(reporter.getTransactions().size()).isEqualTo(1);
    }

    @Test
    void whenTheAPMServerDoesNotSupportsKeepingUnsampledTransactionsUnsampledTransactionsShouldNotBeReported() throws IOException {
        when(apmServerClient.supportsKeepingUnsampledTransaction()).thenReturn(false);
        tracer.getConfig(CoreConfiguration.class).getSampleRate().update(0.0, SpyConfiguration.CONFIG_SOURCE_NAME);

        tracer.startRootTransaction(null).end();

        assertThat(reporter.getTransactions().size()).isEqualTo(0);
    }

    @Test
    void whenTheAPMServerDoesNotSupportsKeepingUnsampledTransactionsSampledTransactionsShouldBeReported() throws IOException {
        when(apmServerClient.supportsKeepingUnsampledTransaction()).thenReturn(false);
        tracer.getConfig(CoreConfiguration.class).getSampleRate().update(1.0, SpyConfiguration.CONFIG_SOURCE_NAME);

        tracer.startRootTransaction(null).end();

        assertThat(reporter.getTransactions().size()).isEqualTo(1);
    }
}
