package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.servlet.helper.JavaxRecordingServletInputStreamWrapper;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import javax.servlet.ServletInputStream;

public class JavaxRequestStreamRecordingInstrumentation extends RequestStreamRecordingInstrumentation {

    @Override
    String typeMatcherClassName() {
        return "javax.servlet.ServletRequest";
    }

    @Override
    String servletInputStreamArgumentClassName() {
        return "javax.servlet.ServletInputStream";
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.servlet.JavaxRequestStreamRecordingInstrumentation$GetInputStreamAdvice";
    }

    @Override
    public String rootClassNameThatClassloaderCanLoad() {
        return "javax.servlet.AsyncContext";
    }

    public static class GetInputStreamAdvice {

        private static final CallDepth callDepth = CallDepth.get(GetInputStreamAdvice.class);

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onReadEnter(@Advice.This Object thiz) {
            callDepth.increment();
        }

        @Nullable
        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
        public static ServletInputStream afterGetInputStream(@Advice.Return @Nullable ServletInputStream inputStream) {
            if (callDepth.isNestedCallAndDecrement() || inputStream == null) {
                return inputStream;
            }
            final Transaction transaction = tracer.currentTransaction();
            // only wrap if the body buffer has been initialized via ServletTransactionHelper.startCaptureBody
            if (transaction != null && transaction.getContext().getRequest().getBodyBuffer() != null) {
                return new JavaxRecordingServletInputStreamWrapper(transaction.getContext().getRequest(), inputStream);
            } else {
                return inputStream;
            }
        }
    }
}
