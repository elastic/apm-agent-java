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

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.metadata.MetaData;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.report.ApmServerClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationRegistry;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DropUnsampledTransactionsTest {

    private static ApmServerClient apmServerClient = mock(ApmServerClient.class);

    private MockReporter reporter = new MockReporter();

    private ElasticApmTracer tracer;

    private void startTracer(double sampleRate) {
        ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        when(configurationRegistry.getConfig(CoreConfiguration.class).getSampleRate()).thenReturn(ConfigurationOption.doubleOption().buildWithDefault(sampleRate));
        tracer = new ElasticApmTracer(configurationRegistry, reporter, new TestObjectPoolFactory(), apmServerClient, "ephemeralId", MetaData.create(configurationRegistry, "ephemeralId"));
        tracer.start(false);
    }

    @AfterEach
    void stopTracer() {
        tracer.stop();
    }

    @Test
    void whenTheAPMServerSupportsKeepingUnsampledTransactionsUnsampledTransactionsShouldBeReported() {
        when(apmServerClient.supportsKeepingUnsampledTransaction()).thenReturn(true);
        startTracer(0.0);

        tracer.startRootTransaction(null).end();

        assertThat(reporter.getTransactions().size()).isEqualTo(1);
    }

    @Test
    void whenTheAPMServerSupportsKeepingUnsampledTransactionsSampledTransactionsShouldBeReported() {
        when(apmServerClient.supportsKeepingUnsampledTransaction()).thenReturn(true);
        startTracer(1.0);

        tracer.startRootTransaction(null).end();

        assertThat(reporter.getTransactions().size()).isEqualTo(1);
    }

    @Test
    void whenTheAPMServerDoesNotSupportsKeepingUnsampledTransactionsUnsampledTransactionsShouldNotBeReported() {
        when(apmServerClient.supportsKeepingUnsampledTransaction()).thenReturn(false);
        startTracer(0.0);

        tracer.startRootTransaction(null).end();

        assertThat(reporter.getTransactions().size()).isEqualTo(0);
    }

    @Test
    void whenTheAPMServerDoesNotSupportsKeepingUnsampledTransactionsSampledTransactionsShouldBeReported() {
        when(apmServerClient.supportsKeepingUnsampledTransaction()).thenReturn(false);
        startTracer(1.0);

        tracer.startRootTransaction(null).end();

        assertThat(reporter.getTransactions().size()).isEqualTo(1);
    }
}
