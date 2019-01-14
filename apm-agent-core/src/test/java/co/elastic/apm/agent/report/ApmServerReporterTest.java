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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Objects;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApmServerReporterTest {

    private ApmServerReporter reporter;
    private ReportingEventHandler reportingEventHandler;

    @BeforeEach
    void setUp() {
        final ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        ReporterConfiguration reporterConfiguration = configurationRegistry.getConfig(ReporterConfiguration.class);
        when(reporterConfiguration.getFlushInterval()).thenReturn(TimeDuration.of("-1ms"));
        when(reporterConfiguration.getMaxQueueSize()).thenReturn(0);
        reportingEventHandler = mock(ReportingEventHandler.class);
        reporter = new ApmServerReporter(true, reporterConfiguration, reportingEventHandler);
    }

    @Test
    void testTransactionProcessor() throws Exception {
        reporter.report(new Transaction(mock(ElasticApmTracer.class)));
        reporter.flush().get();

        assertThat(reporter.getDropped()).isEqualTo(0);
        verify(reportingEventHandler).onEvent(notNull(ReportingEvent::getTransaction), anyLong(), anyBoolean());
    }

    @Test
    void testErrorProcessor() throws Exception {
        reporter.report(new ErrorCapture(MockTracer.create()));
        reporter.flush().get();

        assertThat(reporter.getDropped()).isEqualTo(0);
        verify(reportingEventHandler).onEvent(notNull(ReportingEvent::getError), anyLong(), anyBoolean());
    }

    private <T> T notNull(Function<T, ?> function) {
        return argThat(arg -> Objects.nonNull(function.apply(arg)));
    }
}
