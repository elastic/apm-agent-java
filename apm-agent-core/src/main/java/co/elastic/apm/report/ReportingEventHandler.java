package co.elastic.apm.report;

import com.lmax.disruptor.EventHandler;

public interface ReportingEventHandler extends EventHandler<ReportingEvent> {

    long getReported();

    long getDropped();
}
