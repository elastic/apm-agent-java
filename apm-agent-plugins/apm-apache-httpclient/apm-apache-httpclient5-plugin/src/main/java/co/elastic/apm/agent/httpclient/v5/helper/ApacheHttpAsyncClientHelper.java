package co.elastic.apm.agent.httpclient.v5.helper;


import co.elastic.apm.agent.httpclient.common.AbstractApacheHttpAsyncClientHelper;
import co.elastic.apm.agent.tracer.ElasticContext;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.ObjectPool;
import co.elastic.apm.agent.tracer.pooling.ObjectPoolFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.annotation.Nullable;

public class ApacheHttpAsyncClientHelper implements AbstractApacheHttpAsyncClientHelper<AsyncRequestProducer, AsyncRequestProducerWrapper, FutureCallback, FutureCallbackWrapper<?>, HttpContext> {

    private static final int MAX_POOLED_ELEMENTS = 256;

    private final Tracer tracer;
    private final ObjectPool<AsyncRequestProducerWrapper> requestProducerWrapperObjectPool;
    private final ObjectPool<FutureCallbackWrapper<?>> futureCallbackWrapperObjectPool;

    private final ObjectPool<RequestChannelWrapper> requestChannelWrapperObjectPool;

    public ApacheHttpAsyncClientHelper() {
        tracer = GlobalTracer.get();

        ObjectPoolFactory factory = tracer.getObjectPoolFactory();
        requestProducerWrapperObjectPool = factory.createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new RequestProducerWrapperAllocator());
        futureCallbackWrapperObjectPool = factory.createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new FutureCallbackWrapperAllocator());
        requestChannelWrapperObjectPool = factory.createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new RequestChannelWrapperAllocator());
    }

    private class RequestProducerWrapperAllocator implements Allocator<AsyncRequestProducerWrapper> {
        @Override
        public AsyncRequestProducerWrapper createInstance() {
            return new AsyncRequestProducerWrapper(ApacheHttpAsyncClientHelper.this);
        }
    }

    private class FutureCallbackWrapperAllocator implements Allocator<FutureCallbackWrapper<?>> {
        @Override
        public FutureCallbackWrapper<?> createInstance() {
            return new FutureCallbackWrapper<>(ApacheHttpAsyncClientHelper.this);
        }
    }

    private class RequestChannelWrapperAllocator implements Allocator<RequestChannelWrapper> {
        @Override
        public RequestChannelWrapper createInstance() {
            return new RequestChannelWrapper();
        }
    }

    public AsyncRequestProducerWrapper wrapRequestProducer(AsyncRequestProducer requestProducer, @Nullable Span<?> span,
                                                           @Nullable ElasticContext<?> toPropagate) {
        return requestProducerWrapperObjectPool.createInstance().with(requestProducer, span, toPropagate);
    }

    @Override
    public FutureCallbackWrapper<?> wrapFutureCallback(FutureCallback futureCallback, HttpContext context, Span<?> span) {
        return futureCallbackWrapperObjectPool.createInstance().with(futureCallback, context, span);
    }

    @Override
    public void failedWithoutException(FutureCallbackWrapper<?> cb, Throwable t) {
        cb.failedWithoutExecution(t);
    }

    public RequestChannelWrapper wrapRequestChannel(RequestChannel requestChannel, @Nullable Span<?> span, @Nullable ElasticContext<?> toPropagate) {
        return requestChannelWrapperObjectPool.createInstance().with(requestChannel, span, toPropagate);
    }

    public void recycle(AsyncRequestProducerWrapper requestProducerWrapper) {
        requestProducerWrapperObjectPool.recycle(requestProducerWrapper);
    }

    void recycle(FutureCallbackWrapper<?> futureCallbackWrapper) {
        futureCallbackWrapperObjectPool.recycle(futureCallbackWrapper);
    }

    void recycle(RequestChannelWrapper requestChannelWrapper) {
        requestChannelWrapperObjectPool.recycle(requestChannelWrapper);
    }
}
