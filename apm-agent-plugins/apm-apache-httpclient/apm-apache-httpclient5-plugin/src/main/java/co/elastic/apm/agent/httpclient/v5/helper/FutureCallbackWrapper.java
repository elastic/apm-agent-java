package co.elastic.apm.agent.httpclient.v5.helper;


import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

import javax.annotation.Nullable;

class FutureCallbackWrapper<T> implements FutureCallback<T>, Recyclable {
    private final ApacheHttpAsyncClientHelper helper;
    @Nullable
    private FutureCallback<T> delegate;
    @Nullable
    private HttpContext context;
    private volatile Span<?> span;

    FutureCallbackWrapper(ApacheHttpAsyncClientHelper helper) {
        this.helper = helper;
    }

    FutureCallbackWrapper<T> with(@Nullable FutureCallback<T> delegate, @Nullable HttpContext context, Span<?> span) {
        this.delegate = delegate;
        this.context = context;
        // write to volatile field last
        this.span = span;
        return this;
    }

    @Override
    public void completed(T result) {
        try {
            finishSpan(null);
        } finally {
            if (delegate != null) {
                delegate.completed(result);
            }
            helper.recycle(this);
        }
    }

    @Override
    public void failed(Exception ex) {
        try {
            finishSpan(ex);
        } finally {
            if (delegate != null) {
                delegate.failed(ex);
            }
            helper.recycle(this);
        }
    }

    public void failedWithoutExecution(Throwable ex) {
        try {
            final Span<?> localSpan = span;
            localSpan.captureException(ex);
            localSpan.end();
        } finally {
            helper.recycle(this);
        }
    }

    @Override
    public void cancelled() {
        try {
            finishSpan(null);
        } finally {
            if (delegate != null) {
                delegate.cancelled();
            }
            helper.recycle(this);
        }
    }

    private void finishSpan(@Nullable Exception e) {
        // start by reading the volatile field
        final Span<?> localSpan = span;
        try {
            if (context != null) {
                Object responseObject = context.getAttribute(HttpCoreContext.HTTP_RESPONSE);
                if (responseObject instanceof HttpResponse) {
                    int statusCode = ((HttpResponse) responseObject).getCode();
                    span.getContext().getHttp().withStatusCode(statusCode);
                }
            }
            localSpan.captureException(e);

            if (e != null) {
                localSpan.withOutcome(Outcome.FAILURE);
            }
        } finally {
            localSpan.end();
        }
    }

    @Override
    public void resetState() {
        delegate = null;
        context = null;
        // write to volatile field last
        span = null;
    }
}
