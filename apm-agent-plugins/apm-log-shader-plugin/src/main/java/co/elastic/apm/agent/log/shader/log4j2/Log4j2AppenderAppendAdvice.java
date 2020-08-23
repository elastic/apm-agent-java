package co.elastic.apm.agent.log.shader.log4j2;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;

public class Log4j2AppenderAppendAdvice {

    @SuppressWarnings({"unused"})
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void shadeLoggingEvent(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final LogEvent eventObject,
                                         @Advice.This(typing = Assigner.Typing.DYNAMIC) AbstractOutputStreamAppender<?> thisAppender) {

        AbstractOutputStreamAppender<?> shadeAppender = Log4j2LogShadingHelper.instance().getOrCreateShadeAppenderFor(thisAppender);
        if (shadeAppender != null) {
            shadeAppender.append(eventObject);
        }
    }
}
