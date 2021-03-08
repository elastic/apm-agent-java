package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class IdeaDebugAgentInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.intellij.rt.debugger.agent.CaptureAgent$CaptureTransformer");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("transform").and(returns(byte[].class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        // should not be part of public documentation, and disabling it through configuration is not required
        return Collections.emptySet();
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static byte[] onEnter(@Advice.This ClassFileTransformer transformer,
                                 @Advice.Argument(1) @Nullable String className,
                                 @Advice.Argument(2) @Nullable Class<?> classBeingRedefined,
                                 @Advice.Argument(4) byte[] classBytes) {

        if (className != null && classBeingRedefined != null && className.startsWith("java/util/concurrent")) {
            try {
                return transformer.transform(null, className, null, null, classBytes);
            } catch (IllegalClassFormatException e) {
                throw new IllegalStateException(e);
            }
        }

        return null;
    }

    @Nullable
    @AssignTo.Return
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static byte[] onExit(@Advice.Enter @Nullable byte[] instrumentedClassBytes,
                                @Advice.Return @Nullable byte[] returnValue) {

        return instrumentedClassBytes != null ? instrumentedClassBytes : returnValue;
    }

}
