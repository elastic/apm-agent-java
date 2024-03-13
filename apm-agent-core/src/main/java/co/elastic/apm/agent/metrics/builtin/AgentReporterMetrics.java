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

import co.elastic.apm.agent.configuration.MetricsConfigurationImpl;
import co.elastic.apm.agent.tracer.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricCollector;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.MetricsProvider;
import co.elastic.apm.agent.report.ReporterMonitor;
import co.elastic.apm.agent.report.ReportingEvent;
import co.elastic.apm.agent.report.ReportingEventCounter;
import co.elastic.apm.agent.util.AtomicDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class AgentReporterMetrics implements ReporterMonitor {

    private static final Logger logger = LoggerFactory.getLogger(AgentReporterMetrics.class);

    private final MetricRegistry metricRegistry;

    private static final String TOTAL_EVENTS_METRIC = "agent.events.total";
    private static final String DROPPED_EVENTS_METRIC = "agent.events.dropped";

    private static final String MAX_QUEUE_SIZE_METRIC = "agent.events.queue.max_size.pct";
    private static final String MIN_QUEUE_SIZE_METRIC = "agent.events.queue.min_size.pct";

    private static final String REQUEST_COUNT_METRIC = "agent.events.requests.count";
    private static final String REQUEST_BYTES_METRIC = "agent.events.requests.bytes";

    private final boolean totalEventsMetricEnabled;
    private final boolean droppedEventsMetricEnabled;

    private final boolean minQueueSizeMetricEnabled;

    private final boolean maxQueueSizeMetricEnabled;
    private final boolean requestCountMetricEnabled;

    private final boolean requestBytesMetricEnabled;


    private static final Labels QUEUE_REASON_LABEL = Labels.Mutable.of("reason", "queue").immutableCopy();
    private static final Labels ERROR_REASON_LABEL = Labels.Mutable.of("reason", "error").immutableCopy();

    private static final Labels TRANSACTION_LABEL = Labels.Mutable.of("event_type", "transaction").immutableCopy();
    private static final Labels SPAN_LABEL = Labels.Mutable.of("event_type", "span").immutableCopy();
    private static final Labels ERROR_LABEL = Labels.Mutable.of("event_type", "error").immutableCopy();
    private static final Labels METRICSET_LABEL = Labels.Mutable.of("event_type", "metricset").immutableCopy();
    private static final Labels LOG_LABEL = Labels.Mutable.of("event_type", "log").immutableCopy();

    private static final Labels SUCCESS_LABEL = Labels.Mutable.of("success", "true").immutableCopy();
    private static final Labels FAILURE_LABEL = Labels.Mutable.of("success", "false").immutableCopy();

    private static final Labels GENERIC_QUEUE_LABEL = Labels.Mutable.of("queue_name", "generic").immutableCopy();


    private volatile double currentQueueUtilization = 0;
    private final AtomicDouble maxQueueSize = new AtomicDouble(0.0);
    private final AtomicDouble minQueueSize = new AtomicDouble(0.0);

    public AgentReporterMetrics(final MetricRegistry registry, MetricsConfigurationImpl configuration) {
        this.metricRegistry = registry;
        boolean allEnabled = configuration.isReporterHealthMetricsEnabled();
        this.totalEventsMetricEnabled = allEnabled && !registry.isDisabled(TOTAL_EVENTS_METRIC);
        this.droppedEventsMetricEnabled = allEnabled && !registry.isDisabled(DROPPED_EVENTS_METRIC);
        this.minQueueSizeMetricEnabled = allEnabled && !registry.isDisabled(MIN_QUEUE_SIZE_METRIC);
        this.maxQueueSizeMetricEnabled = allEnabled && !registry.isDisabled(MAX_QUEUE_SIZE_METRIC);
        this.requestCountMetricEnabled = allEnabled && !registry.isDisabled(REQUEST_COUNT_METRIC);
        this.requestBytesMetricEnabled = allEnabled && !registry.isDisabled(REQUEST_BYTES_METRIC);

        if (anyQueueSizeMetricEnabled()) {
            registry.addMetricsProvider(new MetricsProvider() {
                @Override
                public void collectAndReset(MetricCollector collector) {
                    if (minQueueSizeMetricEnabled) {
                        collector.addMetricValue(MIN_QUEUE_SIZE_METRIC, GENERIC_QUEUE_LABEL, minQueueSize.get());
                    }
                    if (maxQueueSizeMetricEnabled) {
                        collector.addMetricValue(MAX_QUEUE_SIZE_METRIC, GENERIC_QUEUE_LABEL, maxQueueSize.get());
                    }
                    double currentUtilization = currentQueueUtilization;
                    minQueueSize.set(currentUtilization);
                    maxQueueSize.set(currentUtilization);
                }
            });
        }
    }

    @Override
    public void eventCreated(ReportingEvent.ReportingEventType eventType, long queueCapacity, long queueSizeAfter) {
        if (totalEventsMetricEnabled) {
            Labels label = getLabelFor(eventType);
            if (label != null) {
                metricRegistry.incrementCounter(TOTAL_EVENTS_METRIC, label);
            }
        }
        updateQueueMetric(queueCapacity, queueSizeAfter);
    }

    private void updateQueueMetric(long queueCapacity, long queueSize) {
        if (anyQueueSizeMetricEnabled()) {
            double queueUtilization = ((double) queueSize) / queueCapacity;
            currentQueueUtilization = queueUtilization;
            maxQueueSize.setWeakMax(queueUtilization);
            minQueueSize.setWeakMin(queueUtilization);
        }
    }

    @Override
    public void eventDequeued(ReportingEvent.ReportingEventType eventType, long queueCapacity, long queueSizeAfter) {
        updateQueueMetric(queueCapacity, queueSizeAfter);
    }

    @Override
    public void eventDroppedBeforeQueue(ReportingEvent.ReportingEventType eventType, long queueCapacity) {
        if (droppedEventsMetricEnabled) {
            Labels label = getLabelFor(eventType);
            if (label != null) {
                metricRegistry.incrementCounter(DROPPED_EVENTS_METRIC, QUEUE_REASON_LABEL);
            }
        }
        updateQueueMetric(queueCapacity, queueCapacity);
    }

    @Override
    public void eventDroppedAfterDequeue(ReportingEvent.ReportingEventType eventType) {
        if (droppedEventsMetricEnabled) {
            Labels label = getLabelFor(eventType);
            if (label != null) {
                metricRegistry.incrementCounter(DROPPED_EVENTS_METRIC, ERROR_REASON_LABEL);
            }
        }
    }


    @Override
    public void requestFinished(ReportingEventCounter requestContent, long acceptedEventCount, long bytesWritten, boolean success) {
        Labels label;
        if (success) {
            label = SUCCESS_LABEL;
        } else {
            label = FAILURE_LABEL;
            if (droppedEventsMetricEnabled) {
                metricRegistry.addToCounter(DROPPED_EVENTS_METRIC, ERROR_REASON_LABEL, requestContent.getTotalCount() - acceptedEventCount);
            }
        }
        if (requestBytesMetricEnabled) {
            metricRegistry.addToCounter(REQUEST_BYTES_METRIC, label, bytesWritten);
        }
        if (requestCountMetricEnabled) {
            metricRegistry.incrementCounter(REQUEST_COUNT_METRIC, label);
        }
    }

    // package-protected for tests
    @Nullable
    static Labels getLabelFor(ReportingEvent.ReportingEventType type) {
        if (type.isControl()) {
            //Control-Events don't matter
            return null;
        }
        switch (type) {
            case TRANSACTION:
                return TRANSACTION_LABEL;
            case SPAN:
                return SPAN_LABEL;
            case ERROR:
                return ERROR_LABEL;
            case METRICSET_JSON_WRITER:
                return METRICSET_LABEL;
            case STRING_LOG:
            case BYTES_LOG:
                return LOG_LABEL;
            default:
                throw new IllegalStateException("Unhandled type: " + type);
        }
    }

    private boolean anyQueueSizeMetricEnabled() {
        return maxQueueSizeMetricEnabled || minQueueSizeMetricEnabled;
    }
}

