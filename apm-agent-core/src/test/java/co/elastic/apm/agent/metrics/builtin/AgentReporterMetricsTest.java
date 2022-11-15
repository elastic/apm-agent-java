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
package co.elastic.apm.agent.metrics.builtin;

import co.elastic.apm.agent.configuration.MetricsConfiguration;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.MetricSet;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.report.ReportingEvent;
import co.elastic.apm.agent.report.ReportingEventCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class AgentReporterMetricsTest {

    private MetricRegistry metricRegistry;

    private ReporterConfiguration mockReporterConfig;

    private AgentReporterMetrics reporterMetrics;

    @BeforeEach
    public void setUp() {
        mockReporterConfig = mock(ReporterConfiguration.class);
        metricRegistry = new MetricRegistry(mockReporterConfig, spy(MetricsConfiguration.class));
    }

    @Test
    public void checkTotalEventCount() {
        reporterMetrics = new AgentReporterMetrics(metricRegistry);

        createNEvents(ReportingEvent.ReportingEventType.WAKEUP, 1);
        createNEvents(ReportingEvent.ReportingEventType.TRANSACTION, 2);
        createNEvents(ReportingEvent.ReportingEventType.SPAN, 3);
        createNEvents(ReportingEvent.ReportingEventType.ERROR, 4);
        createNEvents(ReportingEvent.ReportingEventType.METRICSET_JSON_WRITER, 5);


        reportAndCheckMetrics(metricSets -> {

            assertThat(metricSets).containsOnlyKeys(
                Labels.EMPTY,
                Labels.Mutable.of("event_type", "transaction"),
                Labels.Mutable.of("event_type", "span"),
                Labels.Mutable.of("event_type", "error"),
                Labels.Mutable.of("event_type", "metricset")
            );

            assertThat(metricSets.get(Labels.Mutable.of("event_type", "transaction")).getCounters())
                .hasSize(1)
                .extractingByKey("agent.events.total")
                .satisfies(counter -> assertThat(counter).hasValue(2));
            assertThat(metricSets.get(Labels.Mutable.of("event_type", "span")).getCounters())
                .hasSize(1)
                .extractingByKey("agent.events.total")
                .satisfies(counter -> assertThat(counter).hasValue(3));
            assertThat(metricSets.get(Labels.Mutable.of("event_type", "error")).getCounters())
                .hasSize(1)
                .extractingByKey("agent.events.total")
                .satisfies(counter -> assertThat(counter).hasValue(4));
            assertThat(metricSets.get(Labels.Mutable.of("event_type", "metricset")).getCounters())
                .hasSize(1)
                .extractingByKey("agent.events.total")
                .satisfies(counter -> assertThat(counter).hasValue(5));
            assertThat(metricSets.get(Labels.EMPTY).getCounters())
                .hasSize(0);
        });

    }


    @Test
    public void checkQueueDroppedEventCount() {
        reporterMetrics = new AgentReporterMetrics(metricRegistry);

        dropNEventsBeforeQueue(ReportingEvent.ReportingEventType.WAKEUP, 1); //should not be counted
        dropNEventsBeforeQueue(ReportingEvent.ReportingEventType.TRANSACTION, 2);
        dropNEventsBeforeQueue(ReportingEvent.ReportingEventType.SPAN, 3);
        dropNEventsBeforeQueue(ReportingEvent.ReportingEventType.ERROR, 4);
        dropNEventsBeforeQueue(ReportingEvent.ReportingEventType.METRICSET_JSON_WRITER, 5);

        reportAndCheckMetrics(metricSets -> {
            assertThat(metricSets.get(Labels.EMPTY).getCounters())
                .extractingByKey("agent.events.dropped.queue")
                .satisfies(counter -> assertThat(counter).hasValue(14));
        });
    }


    @Test
    public void checkErrorDroppedEventCount() {
        reporterMetrics = new AgentReporterMetrics(metricRegistry);

        dropNEventsAfterDequeue(ReportingEvent.ReportingEventType.WAKEUP, 1);
        dropNEventsAfterDequeue(ReportingEvent.ReportingEventType.TRANSACTION, 2);
        dropNEventsAfterDequeue(ReportingEvent.ReportingEventType.SPAN, 3);
        dropNEventsAfterDequeue(ReportingEvent.ReportingEventType.ERROR, 4);
        dropNEventsAfterDequeue(ReportingEvent.ReportingEventType.METRICSET_JSON_WRITER, 5);

        ReportingEventCounter inflightEvents = new ReportingEventCounter();
        inflightEvents.add(ReportingEvent.ReportingEventType.TRANSACTION, 20);
        inflightEvents.add(ReportingEvent.ReportingEventType.SPAN, 30);
        inflightEvents.add(ReportingEvent.ReportingEventType.ERROR, 40);
        inflightEvents.add(ReportingEvent.ReportingEventType.METRICSET_JSON_WRITER, 50);

        reporterMetrics.requestFinished(inflightEvents, 100, 0, false);
        //also report a success as sanity check to make sure that they are not counted
        reporterMetrics.requestFinished(inflightEvents, inflightEvents.getTotalCount(), 0, true);

        reportAndCheckMetrics(metricSets -> {

            assertThat(metricSets.get(Labels.EMPTY).getCounters())
                .extractingByKey("agent.events.dropped.error")
                //14 events dropped before queue, 140 sent, server responded with 100 accepted
                .satisfies(counter -> assertThat(counter).hasValue(14 + 140 - 100));
        });
    }


    @Test
    public void checkRequestMetrics() {
        reporterMetrics = new AgentReporterMetrics(metricRegistry);

        ReportingEventCounter inflightEvents = new ReportingEventCounter();

        reporterMetrics.requestFinished(inflightEvents, 0, 100, false);
        reporterMetrics.requestFinished(inflightEvents, 0, 10, true);
        reporterMetrics.requestFinished(inflightEvents, 0, 200, false);
        reporterMetrics.requestFinished(inflightEvents, 0, 20, true);
        reporterMetrics.requestFinished(inflightEvents, 0, 40, true);

        reportAndCheckMetrics(metricSets -> {
            assertThat(metricSets.get(Labels.Mutable.of("success", "true")).getCounters())
                .hasSize(2)
                .satisfies(counters -> {
                    assertThat(counters.get("agent.events.requests.count")).hasValue(3);
                    assertThat(counters.get("agent.events.requests.bytes")).hasValue(70);
                });

            assertThat(metricSets.get(Labels.Mutable.of("success", "false")).getCounters())
                .hasSize(2)
                .satisfies(counters -> {
                    assertThat(counters.get("agent.events.requests.count")).hasValue(2);
                    assertThat(counters.get("agent.events.requests.bytes")).hasValue(300);
                });
        });
    }


    @Test
    public void checkQueueUtilizationCorrectlyReset() {
        reporterMetrics = new AgentReporterMetrics(metricRegistry);

        reporterMetrics.eventCreated(ReportingEvent.ReportingEventType.WAKEUP, 10, 0);
        reporterMetrics.eventDequeued(ReportingEvent.ReportingEventType.WAKEUP, 10, 5);

        reportAndCheckMetrics(metricSets -> {

            assertThat(metricSets.get(Labels.EMPTY).getRawMetrics())
                .containsEntry("agent.events.queue.min_size.pct", 0.0)
                .containsEntry("agent.events.queue.max_size.pct", 0.5);
        });

        reporterMetrics.eventCreated(ReportingEvent.ReportingEventType.WAKEUP, 10, 2);
        reporterMetrics.eventCreated(ReportingEvent.ReportingEventType.WAKEUP, 10, 8);
        reporterMetrics.eventCreated(ReportingEvent.ReportingEventType.WAKEUP, 10, 5);

        reportAndCheckMetrics(metricSets -> {

            assertThat(metricSets.get(Labels.EMPTY).getRawMetrics())
                .containsEntry("agent.events.queue.min_size.pct", 0.2)
                .containsEntry("agent.events.queue.max_size.pct", 0.8);
        });

        //queue die not change since last report, therefore we expect to see the last known state of it
        reportAndCheckMetrics(metricSets -> {

            assertThat(metricSets.get(Labels.EMPTY).getRawMetrics())
                .containsEntry("agent.events.queue.min_size.pct", 0.5)
                .containsEntry("agent.events.queue.max_size.pct", 0.5);
        });

    }

    @Test
    public void checkQueueUtilizationOnDrop() {
        AgentReporterMetrics reporterMetrics = new AgentReporterMetrics(metricRegistry);

        reporterMetrics.eventCreated(ReportingEvent.ReportingEventType.WAKEUP, 10, 0);
        reporterMetrics.eventDroppedBeforeQueue(ReportingEvent.ReportingEventType.WAKEUP, 10);
        reporterMetrics.eventCreated(ReportingEvent.ReportingEventType.WAKEUP, 10, 5);

        reportAndCheckMetrics(metricSets -> {

            assertThat(metricSets.get(Labels.EMPTY).getRawMetrics())
                .containsEntry("agent.events.queue.min_size.pct", 0.0)
                .containsEntry("agent.events.queue.max_size.pct", 1.0);
        });

    }

    @ParameterizedTest
    @ValueSource(strings = {
        "agent.events.total",
        "agent.events.dropped.queue",
        "agent.events.dropped.error",
        "agent.events.queue.min_size.pct",
        "agent.events.queue.max_size.pct"
    })
    public void testDisableMetric(String metric) {
        doReturn(List.of(WildcardMatcher.valueOf(metric))).when(mockReporterConfig).getDisableMetrics();
        AgentReporterMetrics reporterMetrics = new AgentReporterMetrics(metricRegistry);

        //do every possible interaction which could trigger the metric
        reporterMetrics.eventCreated(ReportingEvent.ReportingEventType.TRANSACTION, 10, 5);
        reporterMetrics.eventDroppedBeforeQueue(ReportingEvent.ReportingEventType.TRANSACTION, 10);
        reporterMetrics.eventDroppedAfterDequeue(ReportingEvent.ReportingEventType.TRANSACTION);
        reporterMetrics.eventDequeued(ReportingEvent.ReportingEventType.TRANSACTION, 10, 5);

        ReportingEventCounter inflightEvents = new ReportingEventCounter();
        inflightEvents.add(ReportingEvent.ReportingEventType.TRANSACTION, 20);
        reporterMetrics.requestFinished(inflightEvents, 0, 10, false);
        reporterMetrics.requestFinished(inflightEvents, 0, 20, true);

        reportAndCheckMetrics(metrics -> {
            assertMetricNotExported(metrics, metric);
        });
    }

    @SuppressWarnings("unchecked")
    private void reportAndCheckMetrics(Consumer<Map<Labels, MetricSet>> assertions) {
        metricRegistry.flipPhaseAndReport((metrics) -> {
            assertions.accept(new HashMap<>(metrics));
        });
    }

    private void assertMetricNotExported(Map<Labels, MetricSet> metricSets, String metric) {
        for (MetricSet metricSet : metricSets.values()) {
            assertThat(metricSet.getGauges()).doesNotContainKey(metric);
            assertThat(metricSet.getRawMetrics()).doesNotContainKey(metric);
            assertThat(metricSet.getCounters()).doesNotContainKey(metric);
            assertThat(metricSet.getTimers()).doesNotContainKey(metric);
        }
    }

    private void createNEvents(ReportingEvent.ReportingEventType type, int count) {
        for (int i = 0; i < count; i++) {
            reporterMetrics.eventCreated(type, 1, 0);
        }
    }


    private void dropNEventsBeforeQueue(ReportingEvent.ReportingEventType type, int count) {
        for (int i = 0; i < count; i++) {
            reporterMetrics.eventDroppedBeforeQueue(type, 1);
        }
    }

    private void dropNEventsAfterDequeue(ReportingEvent.ReportingEventType type, int count) {
        for (int i = 0; i < count; i++) {
            reporterMetrics.eventDroppedAfterDequeue(type);
        }
    }

}
