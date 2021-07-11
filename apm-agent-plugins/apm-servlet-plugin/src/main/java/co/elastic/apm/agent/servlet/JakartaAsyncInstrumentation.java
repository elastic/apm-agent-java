package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.servlet.helper.AsyncContextAdviceHelperV2;
import co.elastic.apm.agent.servlet.helper.JakartaAsyncContextAdviceHelperV2;
import jakarta.servlet.AsyncContext;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

public class JakartaAsyncInstrumentation {

    /**
     * Matches
     * <ul>
     * <li>{@link ServletRequest#startAsync()}</li>
     * <li>{@link ServletRequest#startAsync(ServletRequest, ServletResponse)}</li>
     * </ul>
     *
     * @return
     */
    public static class JakartaStartAsyncInstrumentation extends CommonAsyncInstrumentation.StartAsyncInstrumentation {

        @Override
        String servletRequestClassName() {
            return "jakarta.servlet.ServletRequest";
        }

        @Override
        String asyncContextClassName() {
            return "jakarta.servlet.AsyncContext";
        }

        @Override
        String servletResponseClassName() {
            return "jakarta.servlet.ServletResponse";
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.servlet.JakartaAsyncInstrumentation$JakartaStartAsyncInstrumentation$JakartaStartAsyncAdvice";
        }

        public static class JakartaStartAsyncAdvice {
            private static final AsyncContextAdviceHelperV2<AsyncContext> asyncHelper = new JakartaAsyncContextAdviceHelperV2(GlobalTracer.requireTracerImpl());

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void onExitStartAsync(@Advice.Return @Nullable AsyncContext asyncContext) {
                if (asyncContext == null) {
                    return;
                }
                asyncHelper.onExitStartAsync(asyncContext);
            }
        }
    }

    public static class JakartaAsyncContextInstrumentation extends CommonAsyncInstrumentation.AsyncContextInstrumentation {

        @Override
        String asyncContextClassName() {
            return "jakarta.servlet.AsyncContext";
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.servlet.CommonAsyncInstrumentation$AsyncContextInstrumentation$AsyncContextStartAdvice";
        }

    }
}
