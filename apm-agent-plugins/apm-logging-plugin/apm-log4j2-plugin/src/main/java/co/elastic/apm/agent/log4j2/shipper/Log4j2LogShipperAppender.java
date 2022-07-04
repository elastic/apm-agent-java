package co.elastic.apm.agent.log4j2.shipper;

import co.elastic.apm.agent.report.Reporter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.appender.AbstractAppender;

public class Log4j2LogShipperAppender extends AbstractAppender {

    private final Reporter reporter;
    private final StringLayout ecsLayout;

    public Log4j2LogShipperAppender(Reporter reporter, StringLayout ecsLayout) {
        super("ElasticApmAppender", null, ecsLayout, true, null);
        this.reporter = reporter;
        this.ecsLayout = ecsLayout;
    }

    @Override
    public void append(LogEvent event) {
        reporter.shipLog(ecsLayout.toSerializable(event));
    }

}
