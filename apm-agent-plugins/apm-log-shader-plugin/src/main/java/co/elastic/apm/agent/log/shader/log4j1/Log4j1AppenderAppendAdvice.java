package co.elastic.apm.agent.log.shader.log4j1;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.apache.log4j.WriterAppender;
import org.apache.log4j.spi.LoggingEvent;

public class Log4j1AppenderAppendAdvice {

    @SuppressWarnings({"unused"})
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)

    public static void shadeLoggingEvent(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final LoggingEvent eventObject,
                                         @Advice.This(typing = Assigner.Typing.DYNAMIC) WriterAppender thisAppender) {

        WriterAppender shadeAppender = Log4j1LogShadingHelper.instance().getOrCreateShadeAppenderFor(thisAppender);
        if (shadeAppender != null) {
            shadeAppender.append(eventObject);
        }
    }
}
