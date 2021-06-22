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
package co.elastic.apm.agent.report.processor;

import co.elastic.apm.agent.report.ReportingEvent;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import com.lmax.disruptor.EventHandler;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.List;

/**
 * Invokes all registered {@link Processor}s before a {@link ReportingEvent} is processed by
 * the {@link co.elastic.apm.agent.report.ReportingEventHandler}.
 */
public class ProcessorEventHandler implements EventHandler<ReportingEvent> {

    private final List<Processor> processors;

    private ProcessorEventHandler(List<Processor> processors) {
        this.processors = processors;
    }

    public static ProcessorEventHandler loadProcessors(ConfigurationRegistry configurationRegistry) {
        return new ProcessorEventHandler(DependencyInjectingServiceLoader.load(Processor.class, configurationRegistry));
    }

    @Override
    public void onEvent(ReportingEvent event, long sequence, boolean endOfBatch) {
        if (event.getTransaction() != null) {
            for (int i = 0; i < processors.size(); i++) {
                processors.get(i).processBeforeReport(event.getTransaction());
            }
        } else if (event.getError() != null) {
            for (int i = 0; i < processors.size(); i++) {
                processors.get(i).processBeforeReport(event.getError());
            }
        }
    }
}
