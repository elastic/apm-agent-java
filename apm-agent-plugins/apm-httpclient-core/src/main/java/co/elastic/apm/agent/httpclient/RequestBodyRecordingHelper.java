package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.SpanEndListener;
import co.elastic.apm.agent.tracer.metadata.BodyCapture;

class RequestBodyRecordingHelper implements SpanEndListener<Span<?>> {

    /**
     * We do not need to participate in span reference counting here.
     * Instead, we only hold a reference to the span for the time it is not ended.
     * This is ensured via the {@link #onEnd(Span)} callback.
     */
    // Visible for testing
    Span<?> clientSpan;

    public RequestBodyRecordingHelper(Span<?> clientSpan) {
        if (!clientSpan.isFinished()) {
            this.clientSpan = clientSpan;
            clientSpan.addEndListener(this);
        }
    }

    void appendToBody(byte b) {
        if (clientSpan != null) {
            BodyCapture requestBody = clientSpan.getContext().getHttp().getRequestBody();
            requestBody.append(b);
            if (requestBody.isFull()) {
                releaseSpan();
            }
        }
    }

    void appendToBody(byte[] b, int off, int len) {
        if (clientSpan != null) {
            BodyCapture requestBody = clientSpan.getContext().getHttp().getRequestBody();
            requestBody.append(b, off, len);
            if (requestBody.isFull()) {
                releaseSpan();
            }
        }
    }

    void releaseSpan() {
        if (clientSpan != null) {
            clientSpan.removeEndListener(this);
        }
        clientSpan = null;
    }

    @Override
    public void onEnd(Span<?> span) {
        releaseSpan();
    }
}
