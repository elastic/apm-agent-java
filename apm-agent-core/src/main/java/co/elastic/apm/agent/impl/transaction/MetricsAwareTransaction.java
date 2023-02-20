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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.impl.MetricsAwareTracer;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.Timer;
import co.elastic.apm.agent.util.KeyListConcurrentHashMap;

import java.util.List;

public class MetricsAwareTransaction extends Transaction {

    private static final ThreadLocal<Labels.Mutable> labelsThreadLocal = new ThreadLocal<Labels.Mutable>() {
        @Override
        protected Labels.Mutable initialValue() {
            return Labels.Mutable.of();
        }
    };

    public MetricsAwareTransaction(MetricsAwareTracer tracer) {
        super(tracer);
    }

    protected void trackMetrics() {
        try {
            phaser.readerLock();
            phaser.flipPhase();
            // timers are guaranteed to be stable now
            // - no concurrent updates possible as finished is true
            // - no other thread is running the incrementTimer method,
            //   as flipPhase only returns when all threads have exited that method

            final String type = getType();
            if (type == null) {
                return;
            }
            final Labels.Mutable labels = labelsThreadLocal.get();
            labels.resetState();
            labels.serviceName(getTraceContext().getServiceName())
                .serviceVersion(getTraceContext().getServiceVersion())
                .transactionName(name)
                .transactionType(type); // TODO:
            final MetricRegistry metricRegistry = ((MetricsAwareTracer) tracer).getMetricRegistry();
            long criticalValueAtEnter = metricRegistry.writerCriticalSectionEnter();
            try {
                if (collectBreakdownMetrics) {
                    List<String> types = timerBySpanTypeAndSubtype.keyList();
                    for (int i = 0; i < types.size(); i++) {
                        String spanType = types.get(i);
                        KeyListConcurrentHashMap<String, Timer> timerBySubtype = timerBySpanTypeAndSubtype.get(spanType);
                        List<String> subtypes = timerBySubtype.keyList();
                        for (int j = 0; j < subtypes.size(); j++) {
                            String subtype = subtypes.get(j);
                            final Timer timer = timerBySubtype.get(subtype);
                            if (timer.getCount() > 0) {
                                if (subtype.equals("")) {
                                    subtype = null;
                                }
                                labels.spanType(spanType).spanSubType(subtype);
                                metricRegistry.updateTimer("span.self_time", labels, timer.getTotalTimeUs(), timer.getCount());
                                timer.resetState();
                            }
                        }
                    }
                }
            } finally {
                metricRegistry.writerCriticalSectionExit(criticalValueAtEnter);
            }
        } finally {
            phaser.readerUnlock();
        }
    }
}
