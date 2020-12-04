package co.elastic.apm.agent.pluginapi;

import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class SpanInstrumentation extends ApiInstrumentation {
    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public SpanInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.api.AbstractSpanImpl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }


    public static class WithDestinationServiceResourceInstrumentation extends SpanInstrumentation {

        public WithDestinationServiceResourceInstrumentation() {
            super(named("doAppendDestinationServiceResource"));
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void withDestinationServiceResource(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                          @Advice.Argument(0) @Nullable String resource) {
            if (resource != null && context instanceof Span) {
                ((Span) context).getContext().getDestination().getService().withResource(resource);
            }
        }
    }

    public static class WithDestinationServiceNameInstrumentation extends SpanInstrumentation {

        public WithDestinationServiceNameInstrumentation() {
            super(named("doAppendDestinationServiceName"));
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void withDestinationServiceName(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                      @Advice.Argument(0) @Nullable String name) {
            if (name != null && context instanceof Span) {
                ((Span) context).getContext().getDestination().getService().withName(name);
            }
        }
    }

    public static class WithDestinationServiceTypeInstrumentation extends SpanInstrumentation {

        public WithDestinationServiceTypeInstrumentation() {
            super(named("doSetDestinationServiceType"));
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void doSetDestinationServiceType(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Object context,
                                                       @Advice.Argument(0) @Nullable String type) {
            if (type != null && context instanceof Span) {
                ((Span) context).getContext().getDestination().getService().withType(type);
            }
        }
    }
}
