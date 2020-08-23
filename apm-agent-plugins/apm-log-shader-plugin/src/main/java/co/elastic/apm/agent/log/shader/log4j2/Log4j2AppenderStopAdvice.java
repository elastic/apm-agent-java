package co.elastic.apm.agent.log.shader.log4j2;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.FileAppender;

public class Log4j2AppenderStopAdvice {

    @SuppressWarnings({"unused"})
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void shadeLoggingEvent(@Advice.This(typing = Assigner.Typing.DYNAMIC) AbstractOutputStreamAppender<?> thisAppender) {
        if (!(thisAppender instanceof FileAppender)) {
            return;
        }
        Log4j2LogShadingHelper.instance().stopShading(thisAppender);
    }
}
