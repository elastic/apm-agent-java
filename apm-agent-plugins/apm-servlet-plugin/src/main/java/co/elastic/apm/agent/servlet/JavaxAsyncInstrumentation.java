package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.servlet.helper.AsyncContextAdviceHelper;
import co.elastic.apm.agent.servlet.helper.JavaxAsyncContextAdviceHelper;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import javax.servlet.AsyncContext;

public class JavaxAsyncInstrumentation {

    /**
     * Matches
     * <ul>
     * <li>{@link ServletRequest#startAsync()}</li>
     * <li>{@link ServletRequest#startAsync(ServletRequest, ServletResponse)}</li>
     * </ul>
     *
     * @return
     */
    public static class JavaxStartAsyncInstrumentation extends CommonAsyncInstrumentation.StartAsyncInstrumentation {

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
            private static final AsyncContextAdviceHelper<AsyncContext> asyncHelper = new JavaxAsyncContextAdviceHelper(GlobalTracer.requireTracerImpl());;

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void onExitStartAsync(@Advice.Return @Nullable AsyncContext asyncContext) {
                if (asyncContext == null) {
                    return;
                }
                asyncHelper.onExitStartAsync(asyncContext);
            }
        }
    }

    public static class JavaxAsyncContextInstrumentation extends CommonAsyncInstrumentation.AsyncContextInstrumentation {

        @Override
        String asyncContextClassName() {
            return "javax.servlet.AsyncContext";
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.servlet.CommonAsyncInstrumentation$AsyncContextInstrumentation$AsyncContextStartAdvice";
        }

    }
}
