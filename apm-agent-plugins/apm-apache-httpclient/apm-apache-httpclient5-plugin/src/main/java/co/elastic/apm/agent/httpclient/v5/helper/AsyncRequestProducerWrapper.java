package co.elastic.apm.agent.httpclient.v5.helper;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.objectpool.Recyclable;
import org.apache.hc.core5.http.HttpException;
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
    private AbstractSpan<?> parent;

    @Nullable
    private Span span;

    AsyncRequestProducerWrapper(ApacheHttpAsyncClientHelper helper) {
        this.asyncClientHelper = helper;
    }

    public AsyncRequestProducerWrapper with(AsyncRequestProducer delegate, @Nullable Span span,
                                            @Nullable AbstractSpan<?> parent) {
        this.span = span;
        if (parent != null) {
            parent.incrementReferences();
            this.parent = parent;
        }
        this.delegate = delegate;
        return this;
    }

    @Override
    public void resetState() {
        span = null;
        if (parent != null) {
            parent.decrementReferences();
            parent = null;
        }
        delegate = null;
    }

    @Override
    public void sendRequest(RequestChannel requestChannel, HttpContext httpContext) throws HttpException, IOException {
        RequestChannelWrapper wrappedChannel = asyncClientHelper.wrapRequestChannel(requestChannel, span, parent);
        delegate.sendRequest(wrappedChannel, httpContext);
        asyncClientHelper.recycle(wrappedChannel);
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
            asyncClientHelper.recycle(this);
        }
    }
}
