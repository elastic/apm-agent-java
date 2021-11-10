package co.elastic.apm.agent.jsf;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

public class JsfLifecycleRenderInstrumentation extends AbstractJsfLifecycleRenderInstrumentation{
    @Override
    String lifecycleClassName() {
        return "javax.faces.lifecycle.Lifecycle";
    }

    @Override
    String facesContextClassName() {
        return "javax.faces.context.FacesContext";
    }

    public static class AdviceClass {
        private static final String SPAN_ACTION = "render";

        @SuppressWarnings("Duplicates")
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object createRenderSpan() {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }
            if (parent instanceof Span) {
                Span parentSpan = (Span) parent;
                if (SPAN_SUBTYPE.equals(parentSpan.getSubtype()) && SPAN_ACTION.equals(parentSpan.getAction())) {
                    return null;
                }
            }
            Span span = parent.createSpan()
                .withType(SPAN_TYPE)
                .withSubtype(SPAN_SUBTYPE)
                .withAction(SPAN_ACTION)
                .withName("JSF Render");
            span.activate();
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void endRenderSpan(@Advice.Enter @Nullable Object span,
                                         @Advice.Thrown @Nullable Throwable t) {

            if (span instanceof Span) {
                ((Span) span).captureException(t).deactivate().end();
            }
        }
    }
}
