package co.elastic.apm.plugin.api;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class SpanInstrumentation extends ElasticApmInstrumentation {

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public SpanInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.api.SpanImpl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class SetNameInstrumentation extends SpanInstrumentation {
        public SetNameInstrumentation() {
            super(named("setName"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void setName(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Span span,
                                   @Advice.Argument(0) String name) {
            span.setName(name);
        }
    }

    public static class SetTypeInstrumentation extends SpanInstrumentation {
        public SetTypeInstrumentation() {
            super(named("setType"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void setType(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Span span,
                                   @Advice.Argument(0) String type) {
            span.setType(type);
        }
    }

    public static class EndInstrumentation extends SpanInstrumentation {
        public EndInstrumentation() {
            super(named("end"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void end(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) Span span) {
            span.end();
        }
    }
}
