package co.elastic.apm.agent.reactor;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments Mono/Flux to automatically register context-propagation hook
 * <ul>
 *     <li>{@link reactor.core.publisher.Mono#onAssembly}</li>
 *     <li>{@link reactor.core.publisher.Flux#onAssembly}</li>
 * </ul>
 */
@SuppressWarnings("JavadocReference")
public class ReactorInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("reactor.core.publisher.Mono")
            .or(named("reactor.core.publisher.Flux"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isStatic().and(named("onAssembly"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("reactor");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter() {
        TracedSubscriber.registerHooks(tracer);
    }

}
