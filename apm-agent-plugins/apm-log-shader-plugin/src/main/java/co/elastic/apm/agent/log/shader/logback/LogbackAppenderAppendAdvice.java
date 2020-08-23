package co.elastic.apm.agent.log.shader.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class LogbackAppenderAppendAdvice {

    @SuppressWarnings({"unused"})
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void shadeLoggingEvent(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final Object eventObject,
                                         @Advice.This(typing = Assigner.Typing.DYNAMIC) OutputStreamAppender<ILoggingEvent> thisAppender) {
        if (!(thisAppender instanceof FileAppender) || !(eventObject instanceof ILoggingEvent)) {
            return;
        }
        FileAppender<ILoggingEvent> shadeAppender = LogbackLogShadingHelper.instance().getOrCreateShadeAppenderFor((FileAppender<ILoggingEvent>) thisAppender);

        if (shadeAppender != null) {
            // We do not invoke the exact same method we instrument, but a public API that calls it
            shadeAppender.doAppend((ILoggingEvent) eventObject);
        }
    }
}
