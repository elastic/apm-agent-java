package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.servlet.helper.AsyncContextAdviceHelperImpl;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import javax.servlet.AsyncContext;

public class JavaxAsyncInstrumentation {

    static class JavaxStartAsyncInstrumentation extends CommonAsyncInstrumentation.StartAsyncInstrumentation {

        @Override
        String servletRequestClassName() {
            return "javax.servlet.ServletRequest";
        }

        @Override
        String asyncContextClassName() {
            return "javax.servlet.AsyncContext";
        }

        @Override
        String servletResponseClassName() {
            return "javax.servlet.ServletResponse";
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.servlet.JavaxAsyncInstrumentation$JavaxStartAsyncInstrumentation$StartAsyncAdvice";
        }

        public static class StartAsyncAdvice {
            private static final AsyncContextAdviceHelper<AsyncContext> asyncHelper = new AsyncContextAdviceHelperImpl(GlobalTracer.requireTracerImpl());;

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void onExitStartAsync(@Advice.Return @Nullable AsyncContext asyncContext) {
                if (asyncContext == null) {
                    return;
                }
                asyncHelper.onExitStartAsync(asyncContext);
            }
        }
    }
}
