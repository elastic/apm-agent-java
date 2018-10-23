package co.elastic.apm.opentracing.impl;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static co.elastic.apm.opentracing.impl.ApmSpanInstrumentation.OPENTRACING_INSTRUMENTATION_GROUP;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class SpanContextInstrumentation extends ElasticApmInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.opentracing.TraceContextSpanContext");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("baggageItems");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(OPENTRACING_INSTRUMENTATION_GROUP);
    }

    @Advice.OnMethodExit
    public static void baggageItems(@Advice.FieldValue(value = "traceContext", typing = Assigner.Typing.DYNAMIC) @Nullable byte[] traceContext,
                                    @Advice.Return(readOnly = false) Iterable<Map.Entry<String, String>> baggage) {
        if (traceContext != null) {
            baggage = doGetBaggage(traceContext);
        }
    }

    @VisibleForAdvice
    public static Iterable<Map.Entry<String, String>> doGetBaggage(byte[] traceContext) {
        final TraceContext context = TraceContext.with64BitId();
        TraceContext.fromSerialized().asChildOf(context, traceContext);
        String traceParentHeader = context.getIncomingTraceParentHeader();
        return Collections.singletonMap(TraceContext.TRACE_PARENT_HEADER, traceParentHeader).entrySet();
    }
}
