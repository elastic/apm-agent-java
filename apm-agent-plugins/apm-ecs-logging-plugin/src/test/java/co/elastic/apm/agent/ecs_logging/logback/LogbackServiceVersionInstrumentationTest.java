package co.elastic.apm.agent.ecs_logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import co.elastic.apm.agent.ecs_logging.EcsServiceVersionTest;
import co.elastic.logging.logback.EcsEncoder;

public class LogbackServiceVersionInstrumentationTest extends EcsServiceVersionTest {

    private EcsEncoder ecsEncoder;

    @Override
    protected String createLogMsg() {
        LoggerContext loggerContext = new LoggerContext();
        Logger logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        ILoggingEvent event = new LoggingEvent("co.elastic.apm.agent.ecs_logging.logback.LogbackServiceNameInstrumentationTest",logger, Level.INFO, "msg", null, null);
        return new String(ecsEncoder.encode(event));
    }

    @Override
    protected void initFormatterWithoutServiceVersionSet() {
        ecsEncoder = new EcsEncoder();
    }

    @Override
    protected void initFormatterWithServiceVersion(String version) {
        ecsEncoder = new EcsEncoder();
        ecsEncoder.setServiceVersion(version);
    }
}
