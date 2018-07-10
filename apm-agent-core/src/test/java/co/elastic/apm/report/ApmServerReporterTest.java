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
package co.elastic.apm.report;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.processor.ProcessorEventHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApmServerReporterTest {

    private ApmServerReporter reporter;

    @BeforeEach
    void setUp() {
        final ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        ReporterConfiguration reporterConfiguration = configurationRegistry.getConfig(ReporterConfiguration.class);
        when(reporterConfiguration.getFlushInterval()).thenReturn(-1);
        when(reporterConfiguration.getMaxQueueSize()).thenReturn(0);
        SystemInfo system = new SystemInfo("x64", "localhost", "platform");
        reporter = new ApmServerReporter(new Service(), new ProcessInfo("title"), system,
            mock(PayloadSender.class), true, reporterConfiguration,
            new ProcessorEventHandler(Collections.singletonList(new TestProcessor())),
            configurationRegistry.getConfig(CoreConfiguration.class));
    }

    @Test
    void testTransactionProcessor() throws ExecutionException, InterruptedException {
        final int transactionCount = TestProcessor.getTransactionCount();
        reporter.report(new Transaction(null));
        reporter.flush().get();

        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(TestProcessor.getTransactionCount()).isEqualTo(transactionCount + 1);
    }

    @Test
    void testErrorProcessor() throws ExecutionException, InterruptedException {
        final int errorCount = TestProcessor.getErrorCount();
        reporter.report(new ErrorCapture());
        reporter.flush().get();

        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(TestProcessor.getErrorCount()).isEqualTo(errorCount + 1);
    }
}
