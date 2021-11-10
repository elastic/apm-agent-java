package co.elastic.apm.agent.jsf;

import net.bytebuddy.asm.Advice;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;

public class JsfLifecycleExecuteInstrumentation extends AbstractJsfLifecycleExecuteInstrumentation {

    @Override
    String lifecycleClassName() {
        return "javax.faces.lifecycle.Lifecycle";
    }

    @Override
    String facesContextClassName() {
        return "javax.faces.context.FacesContext";
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
