package co.elastic.apm.agent.jsf;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class JakartaeeJsfLifecycleExecuteInstrumentation extends AbstractJsfLifecycleExecuteInstrumentation {

    @Override
    String lifecycleClassName() {
        return "jakarta.faces.lifecycle.Lifecycle";
    }

    @Override
    String facesContextClassName() {
        return "jakarta.faces.context.FacesContext";
    }

    public static class AdviceClass extends BaseExecuteAdvice {

        @Nullable
        @SuppressWarnings("Duplicates")
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object createExecuteSpan(@Advice.Argument(0) @Nonnull FacesContext facesContext) {
            boolean withExternalContext = false;
            String requestServletPath = null;
            String requestPathInfo = null;
            ExternalContext externalContext = facesContext.getExternalContext();
            if (externalContext != null) {
                withExternalContext = true;
                requestServletPath = externalContext.getRequestServletPath();
                requestPathInfo = externalContext.getRequestPathInfo();
            }
            return createAndActivateSpan(withExternalContext, requestServletPath, requestPathInfo);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void endExecuteSpan(@Advice.Enter @Nullable Object span,
                                          @Advice.Thrown @Nullable Throwable t) {
            endAndDeactivateSpan(span, t);
        }
    }
}
