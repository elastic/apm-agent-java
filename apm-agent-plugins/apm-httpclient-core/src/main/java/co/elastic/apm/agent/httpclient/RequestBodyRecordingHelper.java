package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.SpanEndListener;
import co.elastic.apm.agent.tracer.metadata.BodyCapture;

public class RequestBodyRecordingHelper implements SpanEndListener<Span<?>> {

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


    /**
     * @param b the byte to append
     * @return false, if the body buffer is full and future calls would be no-op. True otherwise.
     */
    public boolean appendToBody(byte b) {
        if (clientSpan != null) {
            BodyCapture requestBody = clientSpan.getContext().getHttp().getRequestBody();
            requestBody.append(b);
            if (requestBody.isFull()) {
                releaseSpan();
            } else {
                return true;
            }
        }
        return false;
    }

    public void appendToBody(byte[] b, int off, int len) {
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
