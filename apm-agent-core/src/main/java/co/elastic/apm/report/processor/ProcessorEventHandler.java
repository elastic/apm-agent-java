package co.elastic.apm.report.processor;

import co.elastic.apm.report.ReportingEvent;
import com.lmax.disruptor.EventHandler;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.ServiceLoader;

/**
 * Invokes all registered {@link Processor}s before a {@link ReportingEvent} is processed by
 * the {@link co.elastic.apm.report.ReportingEventHandler}.
 */
public class ProcessorEventHandler implements EventHandler<ReportingEvent> {

    private final Iterable<Processor> processors;

    public ProcessorEventHandler(Iterable<Processor> processors) {
        this.processors = processors;
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
            for (Processor processor : processors) {
                processor.processBeforeReport(event.getTransaction());
            }
        } else if (event.getError() != null) {
            for (Processor processor : processors) {
                processor.processBeforeReport(event.getError());
            }
        }
    }
}
