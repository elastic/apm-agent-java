package co.elastic.apm.agent.log4j1.shipper;

import co.elastic.apm.agent.report.Reporter;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

public class LogShipperAppender extends AppenderSkeleton {
    private final Reporter reporter;
    private final Layout formatter;

    public LogShipperAppender(Reporter reporter, Layout formatter) {
        this.reporter = reporter;
        this.formatter = formatter;
    }

    @Override
    public synchronized void doAppend(LoggingEvent event) {
        append(event);
    }

    @Override
    protected void append(LoggingEvent event) {
        reporter.shipLog(formatter.format(event));
    }

    @Override
    public void close() {

    }

    @Override
    public boolean requiresLayout() {
        return false;
    }
}
