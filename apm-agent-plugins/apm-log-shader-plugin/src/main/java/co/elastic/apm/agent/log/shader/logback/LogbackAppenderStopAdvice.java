package co.elastic.apm.agent.log.shader.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class LogbackAppenderStopAdvice {

    @SuppressWarnings({"unused"})
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void shadeLoggingEvent(@Advice.This(typing = Assigner.Typing.DYNAMIC) OutputStreamAppender<ILoggingEvent> thisAppender) {
        if (!(thisAppender instanceof FileAppender)) {
            return;
        }
        LogbackLogShadingHelper.instance().stopShading((FileAppender<ILoggingEvent>) thisAppender);
    }
}
