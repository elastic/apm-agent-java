/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
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
package co.elastic.apm.agent.report.processor;

import co.elastic.apm.agent.report.ReportingEvent;
import com.lmax.disruptor.EventHandler;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Invokes all registered {@link Processor}s before a {@link ReportingEvent} is processed by
 * the {@link co.elastic.apm.agent.report.ReportingEventHandler}.
 */
public class ProcessorEventHandler implements EventHandler<ReportingEvent> {

    private final List<Processor> processors;

    public ProcessorEventHandler(Iterable<Processor> processors) {
        this.processors = new ArrayList<>();
        for (Processor processor : processors) {
            this.processors.add(processor);
        }
    }

    public static ProcessorEventHandler loadProcessors(ConfigurationRegistry configurationRegistry) {
        final ServiceLoader<Processor> processors = ServiceLoader.load(Processor.class, ProcessorEventHandler.class.getClassLoader());
        for (Processor processor : processors) {
            processor.init(configurationRegistry);
        }
        return new ProcessorEventHandler(processors);
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
