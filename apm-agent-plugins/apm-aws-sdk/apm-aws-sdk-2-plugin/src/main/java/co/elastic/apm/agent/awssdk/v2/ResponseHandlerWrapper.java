package co.elastic.apm.agent.awssdk.v2;

import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.core.Response;
import software.amazon.awssdk.core.internal.http.TransformingAsyncResponseHandler;
import software.amazon.awssdk.http.SdkHttpResponse;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class ResponseHandlerWrapper<T> implements TransformingAsyncResponseHandler<Response<T>> {

    private final TransformingAsyncResponseHandler<Response<T>> delegate;
    private final Span span;

    public ResponseHandlerWrapper(TransformingAsyncResponseHandler<Response<T>> delegate, Span span) {
        this.delegate = delegate;
        this.span = span;
    }

    @Override
    public CompletableFuture<Response<T>> prepare() {
        CompletableFuture<Response<T>> delegateFuture = delegate.prepare();
        delegateFuture.whenComplete((r, t) -> {
            if (t != null) {
                span.captureException(t);
                span.withOutcome(Outcome.FAILURE);
            } else if (r.exception() != null) {
                span.captureException(r.exception());
                span.withOutcome(Outcome.FAILURE);
            } else {
                span.withOutcome(Outcome.SUCCESS);
            }
            span.end();
        });
        return delegateFuture;
    }

    @Override
    public void onHeaders(SdkHttpResponse sdkHttpResponse) {
        delegate.onHeaders(sdkHttpResponse);
    }

    @Override
    public void onStream(Publisher<ByteBuffer> publisher) {
        delegate.onStream(publisher);
    }

    @Override
    public void onError(Throwable throwable) {
        if (!span.isFinished()) {
            span.captureException(throwable);
            span.withOutcome(Outcome.FAILURE);
        }

        delegate.onError(throwable);
    }
}
