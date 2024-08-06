package co.elastic.apm.agent.tracer;

public interface SpanEndListener<T extends Span<?>> {

    /**
     * Invoked when the span is being ended.
     *
     * @param span the span being ended
     */
    void onEnd(T span);
}
