package co.elastic.apm.agent.opentelemetry;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.opentelemetry.sdk.ElasticOpenTelemetry;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import io.opentelemetry.api.OpenTelemetry;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class GlobalOpenTelemetryInstrumentation extends TracerAwareInstrumentation {

    private static final ElasticOpenTelemetry ELASTIC_OPEN_TELEMETRY = new ElasticOpenTelemetry(GlobalTracer.requireTracerImpl());

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
        return Collections.singleton("opentelemetry");
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
