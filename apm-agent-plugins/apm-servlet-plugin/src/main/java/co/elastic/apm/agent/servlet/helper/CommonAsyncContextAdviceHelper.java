package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.servlet.CommonAsyncInstrumentation;
import co.elastic.apm.agent.servlet.ServletApiAdvice;
import co.elastic.apm.agent.servlet.ServletTransactionHelper;
import org.jctools.queues.atomic.AtomicQueueFactory;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

public abstract class CommonAsyncContextAdviceHelper<T> implements CommonAsyncInstrumentation.AsyncContextAdviceHelper<T> {

    private static final String ASYNC_LISTENER_ADDED = ServletApiAdvice.class.getName() + ".asyncListenerAdded";
    private static final int MAX_POOLED_ELEMENTS = 256;

    private final ObjectPool<ApmAsyncListener> asyncListenerObjectPool;
    private final ServletTransactionHelper servletTransactionHelper;
    private final Tracer tracer;

    public CommonAsyncContextAdviceHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        servletTransactionHelper = new ServletTransactionHelper(tracer);

        asyncListenerObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<ApmAsyncListener>newQueue(createBoundedMpmc(MAX_POOLED_ELEMENTS)),
            false,
            new CommonAsyncContextAdviceHelper.ApmAsyncListenerAllocator());
    }

    ServletTransactionHelper getServletTransactionHelper() {
        return servletTransactionHelper;
    }

    private final class ApmAsyncListenerAllocator implements Allocator<T> {
        @Override
        public T createInstance() {
            return new ApmAsyncListener(CommonAsyncContextAdviceHelper.this);
        }
    }

}
