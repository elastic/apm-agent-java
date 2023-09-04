package co.elastic.apm.agent.micronaut;

import co.elastic.apm.agent.tracer.Activateable;
import co.elastic.apm.agent.tracer.Transaction;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.MutableHttpResponse;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

public class RequestLifecycleAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(
            @Advice.This Object untypedThis,
            @Advice.FieldValue("request") @Nullable Object requestUntyped
        ) {
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
        @Advice.Return(readOnly = false) @Nullable ExecutionFlow<MutableHttpResponse<?>> returnFlow,
        @Advice.Thrown @Nullable Throwable t) {

        PropagatedContext.Scope scope = (PropagatedContext.Scope) scopeUntyped;

        if(scope == null) {
            return;
        }

        scope.close();

        if(returnFlow == null) {
            return;
        }

        returnFlow.onComplete( (response, exception) -> {
            PropagatedContextElement context = PropagatedContext.get().get(PropagatedContextElement.class);

            if(context == null) {
                return;
            }

            Transaction<?> trx = context.getTransaction();

            if(exception != null) {
                trx.captureException(exception);
            }

            trx.end();
        });

    }
}
