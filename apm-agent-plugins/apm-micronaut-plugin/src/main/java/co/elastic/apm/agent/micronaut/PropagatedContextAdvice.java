package co.elastic.apm.agent.micronaut;

import co.elastic.apm.agent.tracer.Activateable;
import io.micronaut.core.propagation.PropagatedContext;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

public class PropagatedContextAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(@Advice.This Object untypedThis) {
        if(!(untypedThis instanceof PropagatedContext)) {
            return null;
        }

        PropagatedContext typedThis = (PropagatedContext) untypedThis;

        PropagatedContextElement elasticContext = typedThis.get(PropagatedContextElement.class);

        if(elasticContext == null) {
            return null;
        }

        return elasticContext.getTransaction().activate();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable Object scopeUntyped,
        @Advice.Thrown @Nullable Throwable t) {
        if (scopeUntyped == null) {
            return;
        }

        Activateable<?> scope = (Activateable<?>) scopeUntyped;
        scope.deactivate();
    }
}
