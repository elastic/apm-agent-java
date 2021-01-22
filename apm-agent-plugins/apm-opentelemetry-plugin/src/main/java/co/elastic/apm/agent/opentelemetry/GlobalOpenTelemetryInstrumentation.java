package co.elastic.apm.agent.opentelemetry;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.opentelemetry.sdk.ElasticOTelTracer;
import co.elastic.apm.agent.opentelemetry.sdk.ElasticOTelTracerProvider;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import io.opentelemetry.api.DefaultOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class GlobalOpenTelemetryInstrumentation extends TracerAwareInstrumentation {

    private static final OpenTelemetry ELASTIC_OPEN_TELEMETRY = DefaultOpenTelemetry.builder()
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .setTracerProvider(new ElasticOTelTracerProvider(new ElasticOTelTracer(GlobalTracer.requireTracerImpl())))
        .build();

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.opentelemetry.api.GlobalOpenTelemetry");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("get");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("opentelemetry", "experimental");
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false, skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter() {
        // skips actual method and directly goes to exit advice
        return true;
    }

    @AssignTo.Return
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static OpenTelemetry onExit() {
        return ELASTIC_OPEN_TELEMETRY;
    }
}
