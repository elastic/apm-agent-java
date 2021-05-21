package co.elastic.apm.agent.vertx;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import io.vertx.core.Handler;

public class SetTimerWrapper extends GenericHandlerWrapper<Long> {

    /**
     * Use this thread local to prevent tracking of endless, recursive timer jobs
     */
    private static final ThreadLocal<String> activeTimerHandlerPerThread = new ThreadLocal<>();

    @Override
    public void handle(Long event) {
        activeTimerHandlerPerThread.set(actualHandler.getClass().getName());
        try {
            super.handle(event);
        } finally {
            activeTimerHandlerPerThread.remove();
        }
    }

    public SetTimerWrapper(AbstractSpan<?> parentSpan, Handler<Long> actualHandler) {
        super(parentSpan, actualHandler);
    }

    public static Handler<Long> wrapTimerIfActiveSpan(Handler<Long> handler) {
        AbstractSpan<?> currentSpan = GlobalTracer.get().getActive();

        // do not wrap if there is no parent span or if we are in the recursive context of the same type of timer
        if (currentSpan != null && !handler.getClass().getName().equals(activeTimerHandlerPerThread.get())) {
            handler = new SetTimerWrapper(currentSpan, handler);
        }

        return handler;
    }

}
