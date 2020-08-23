package co.elastic.apm.agent.log.shader.log4j1;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.apache.log4j.WriterAppender;

public class Log4j1AppenderStopAdvice {

    @SuppressWarnings({"unused"})
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)

    public static void shadeLoggingEvent(@Advice.This(typing = Assigner.Typing.DYNAMIC) WriterAppender thisAppender) {
        Log4j1LogShadingHelper.instance().stopShading(thisAppender);
    }
}
