package co.elastic.apm.agent.redis.lettuce;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;

import javax.annotation.Nullable;

public class LettuceUtil {

    @VisibleForAdvice
    public static void beforeComplete(@Nullable Throwable t) {
        TraceContextHolder<?> active = ElasticApmInstrumentation.getActive();
        if (active instanceof Span) {
            Span activeSpan = (Span) active;
            if ("redis".equals(activeSpan.getSubtype())) {
                activeSpan
                    .captureException(t)
                    .end();
            }
        }
    }
}
