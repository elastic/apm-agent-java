package co.elastic.apm.agent.logback.shipper;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import co.elastic.apm.agent.report.Reporter;

public class LogbackLogShipperAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private final Reporter reporter;
    private final Encoder<ILoggingEvent> formatter;

    public LogbackLogShipperAppender(Reporter reporter, Encoder<ILoggingEvent> formatter) {
        this.reporter = reporter;
        this.formatter = formatter;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        reporter.shipLog(formatter.encode(eventObject));
    }
}
