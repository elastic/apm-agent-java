package co.elastic.apm.agent.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import co.elastic.apm.agent.logging.LogEcsReformatting;

/**
 * This is not really an {@link ch.qos.logback.core.OutputStreamAppender Appender}, it is just used in order to hold the
 * {@link ch.qos.logback.core.encoder.Encoder Encoder} of the original appender. This way, we can use the same map
 * between the original appender and the corresponding ECS-reformatting counterpart, regardless of the configured
 * {@link LogEcsReformatting} - when {@link LogEcsReformatting#SHADE SHADE} or {@link LogEcsReformatting#REPLACE REPLACE}
 * is used, a real ECS appender will be mapped to them. When {@link LogEcsReformatting#OVERRIDE OVERRIDE} is used, we
 * override the original appender's encoder with an {@link co.elastic.logging.logback.EcsEncoder EcsEncoder} and use
 * this mock appender to hold a reference to the original encoder, so that we can restore it when configuration changes.
 */
public class OriginalEncoderHolder extends OutputStreamAppender<ILoggingEvent> {
    @Override
    public void doAppend(ILoggingEvent eventObject) {
        // this Appender should not append anything, it is just used as an EcsEncoder holder
    }

    @Override
    public void stop() {
        // nothing to stop
    }
}
