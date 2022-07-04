package co.elastic.apm.agent.jul.shipper;

import co.elastic.apm.agent.report.Reporter;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class JulLogShipperHandler extends Handler {
    private final Reporter reporter;
    private final Formatter formatter;

    public JulLogShipperHandler(Reporter reporter, Formatter formatter) {
        this.reporter = reporter;
        this.formatter = formatter;
    }

    @Override
    public void publish(LogRecord record) {
        reporter.shipLog(formatter.format(record));
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() throws SecurityException {

    }
}
