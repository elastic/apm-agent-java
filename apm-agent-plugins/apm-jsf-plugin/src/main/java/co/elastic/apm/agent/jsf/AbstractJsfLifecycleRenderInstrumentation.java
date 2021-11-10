package co.elastic.apm.agent.jsf;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class AbstractJsfLifecycleRenderInstrumentation extends AbstractJsfLifecycleInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("render")
            .and(takesArguments(1))
            .and(takesArgument(0, named(facesContextClassName())));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        List<String> ret = new ArrayList<>(super.getInstrumentationGroupNames());
        ret.add("render");
        return ret;
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.jsf.AbstractJsfLifecycleRenderInstrumentation$AdviceClass";
    }

    abstract String facesContextClassName();

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
