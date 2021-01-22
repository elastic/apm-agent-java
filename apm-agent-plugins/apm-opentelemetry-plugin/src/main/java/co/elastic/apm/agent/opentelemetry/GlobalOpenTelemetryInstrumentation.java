package co.elastic.apm.agent.opentelemetry;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.opentelemetry.sdk.ElasticOpenTelemetry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class GlobalOpenTelemetryInstrumentation extends TracerAwareInstrumentation {
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

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.FieldValue("globalOpenTelemetry") @Nullable OpenTelemetry globalOpenTelemetry) {
        if (globalOpenTelemetry == null) {
            GlobalOpenTelemetry.set(new ElasticOpenTelemetry(GlobalTracer.requireTracerImpl()));
        }
    }
}
