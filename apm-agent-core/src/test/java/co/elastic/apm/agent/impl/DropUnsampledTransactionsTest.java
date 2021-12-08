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
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.report.ApmServerClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationRegistry;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DropUnsampledTransactionsTest {

    private static ElasticApmTracer tracer;

    private static MockReporter reporter;

    private static ApmServerClient apmServerClient;

    @BeforeAll
    static void startTracer() {
        reporter = new MockReporter();

        apmServerClient = mock(ApmServerClient.class);

        ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        when(configurationRegistry.getConfig(CoreConfiguration.class).getSampleRate()).thenReturn(ConfigurationOption.doubleOption().buildWithDefault(0.5));

        tracer = new ElasticApmTracer(configurationRegistry, reporter, new TestObjectPoolFactory(), apmServerClient, "ephemeralId", MetaData.create(configurationRegistry, "ephemeralId"));
        tracer.start(false);
    }

    @BeforeEach
    void resetReporter() {
        reporter.reset();
    }

    @AfterAll
    static void stopTracer() {
        tracer.stop();
    }

    @Test
    void testAPMServerSupportsKeepingUnsampledTransactions() {
        when(apmServerClient.supportsKeepingUnsampledTransaction()).thenReturn(true);

        int sampledTransactions = 0;
        for (int i = 0; i < 10; ++i) {
            Transaction transaction = tracer.startRootTransaction(null);
            if (transaction.isSampled()) {
                ++sampledTransactions;
            }
            transaction.end();
        }

        assertThat(sampledTransactions).isLessThan(10);
        assertThat(reporter.getTransactions().size()).isEqualTo(10);
    }

    @Test
    void testAPMServerDoesNotSupportsKeepingUnsampledTransactions() {
        when(apmServerClient.supportsKeepingUnsampledTransaction()).thenReturn(false);

        int sampledTransactions = 0;
        for (int i = 0; i < 10; ++i) {
            Transaction transaction = tracer.startRootTransaction(null);
            if (transaction.isSampled()) {
                ++sampledTransactions;
            }
            transaction.end();
        }

        assertThat(sampledTransactions).isLessThan(10);
        assertThat(reporter.getTransactions().size()).isEqualTo(sampledTransactions);
    }
}
