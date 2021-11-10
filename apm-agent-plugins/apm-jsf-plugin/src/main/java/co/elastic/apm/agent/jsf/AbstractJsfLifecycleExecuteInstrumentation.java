package co.elastic.apm.agent.jsf;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class AbstractJsfLifecycleExecuteInstrumentation extends AbstractJsfLifecycleInstrumentation {
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(1))
            .and(takesArgument(0, named(facesContextClassName())));
    }

    abstract String facesContextClassName();

    static class BaseExecuteAdvice {
        private static final String SPAN_ACTION = "execute";

        @Nullable
        protected static Object createAndActivateSpan(boolean withExternalContext, @Nullable String requestServletPath, @Nullable String requestPathInfo) {
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
            Transaction transaction = tracer.currentTransaction();
            if (transaction != null) {
                try {
                    if (withExternalContext) {
                        transaction.withName(requestServletPath, PRIO_HIGH_LEVEL_FRAMEWORK);
                        if (requestPathInfo != null) {
                            transaction.appendToName(requestPathInfo, PRIO_HIGH_LEVEL_FRAMEWORK);
                        }
                    }
                    transaction.setFrameworkName(FRAMEWORK_NAME);
                } catch (Exception e) {
                    // do nothing- rely on the default servlet name logic
                }
            }
            Span span = parent.createSpan()
                .withType(SPAN_TYPE)
                .withSubtype(SPAN_SUBTYPE)
                .withAction(SPAN_ACTION)
                .withName("JSF Execute");
            span.activate();
            return span;
        }

        protected static void endAndDeactivateSpan(@Nullable Object span, @Nullable Throwable t) {
            if (span instanceof Span) {
                ((Span) span).captureException(t).deactivate().end();
            }
        }
    }
}
