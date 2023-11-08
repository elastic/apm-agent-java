package co.elastic.apm.agent.httpclient.v5.helper;


import co.elastic.apm.agent.tracer.ElasticContext;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.annotation.Nullable;
import java.io.IOException;

public class AsyncRequestProducerWrapper implements AsyncRequestProducer, Recyclable {

    private final ApacheHttpAsyncClientHelper asyncClientHelper;
    private volatile AsyncRequestProducer delegate;

    @Nullable
    private ElasticContext<?> toPropagate;

    @Nullable
    private Span<?> span;

    AsyncRequestProducerWrapper(ApacheHttpAsyncClientHelper helper) {
        this.asyncClientHelper = helper;
    }

    public AsyncRequestProducerWrapper with(AsyncRequestProducer delegate, @Nullable Span<?> span,
                                            @Nullable ElasticContext<?> toPropagate) {
        this.span = span;
        if (null != toPropagate) {
            toPropagate.incrementReferences();
            this.toPropagate = toPropagate;
        }
        this.delegate = delegate;
        return this;
    }

    @Override
    public void sendRequest(RequestChannel requestChannel, HttpContext httpContext) throws HttpException, IOException {
        RequestChannelWrapper wrappedRequestChannel = null;
        try {
            wrappedRequestChannel = asyncClientHelper.wrapRequestChannel(requestChannel, span, toPropagate);
        } finally {
            boolean isNotNullWrappedRequestChannel = null != wrappedRequestChannel;
            delegate.sendRequest(isNotNullWrappedRequestChannel ? wrappedRequestChannel : requestChannel, httpContext);
            if (isNotNullWrappedRequestChannel) {
                asyncClientHelper.recycle(wrappedRequestChannel);
            }
        }
    }

    @Override
    public boolean isRepeatable() {
        return delegate.isRepeatable();
    }

    @Override
    public void failed(Exception e) {
        delegate.failed(e);
    }

    @Override
    public int available() {
        return delegate.available();
    }

    @Override
    public void produce(DataStreamChannel dataStreamChannel) throws IOException {
        delegate.produce(dataStreamChannel);
    }

    @Override
    public void releaseResources() {
        if (delegate != null) {
            delegate.releaseResources();
        }
        asyncClientHelper.recycle(this);
    }

    @Override
    public void resetState() {
        span = null;
        if (toPropagate != null) {
            toPropagate.decrementReferences();
            toPropagate = null;
        }
        delegate = null;
    }

}
