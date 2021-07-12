package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.servlet.ServletTransactionHelper;
import org.jctools.queues.atomic.AtomicQueueFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;

import static co.elastic.apm.agent.servlet.ServletTransactionHelper.ASYNC_ATTRIBUTE;
import static co.elastic.apm.agent.servlet.ServletTransactionHelper.TRANSACTION_ATTRIBUTE;
import static co.elastic.apm.agent.servlet.helper.AsyncConstants.ASYNC_LISTENER_ADDED;
import static co.elastic.apm.agent.servlet.helper.AsyncConstants.MAX_POOLED_ELEMENTS;
import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

public class JavaxAsyncContextAdviceHelper implements AsyncContextAdviceHelper<AsyncContext> {

    private final ObjectPool<JavaxApmAsyncListener> asyncListenerObjectPool;
    private final ServletTransactionHelper servletTransactionHelper;
    private final Tracer tracer;

    public JavaxAsyncContextAdviceHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        servletTransactionHelper = new ServletTransactionHelper(tracer);

        asyncListenerObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<JavaxApmAsyncListener>newQueue(createBoundedMpmc(MAX_POOLED_ELEMENTS)),
            false,
            new JavaxAsyncContextAdviceHelper.ApmAsyncListenerAllocator());
    }

    private final class ApmAsyncListenerAllocator implements Allocator<JavaxApmAsyncListener> {
        @Override
        public JavaxApmAsyncListener createInstance() {
            return new JavaxApmAsyncListener(JavaxAsyncContextAdviceHelper.this);
        }
    }


    ServletTransactionHelper getServletTransactionHelper() {
        return servletTransactionHelper;
    }

    @Override
    public void onExitStartAsync(AsyncContext asyncContext) {
        final ServletRequest request = asyncContext.getRequest();
        if (request.getAttribute(ASYNC_LISTENER_ADDED) != null) {
            return;
        }
        final Transaction transaction = tracer.currentTransaction();
        if (transaction != null && transaction.isSampled() && request.getAttribute(ASYNC_LISTENER_ADDED) == null) {
            // makes sure that the listener is only added once, even if the request is wrapped
            // which leads to multiple invocations of startAsync for the same underlying request
            request.setAttribute(ASYNC_LISTENER_ADDED, Boolean.TRUE);
            // specifying the request and response is important
            // otherwise AsyncEvent.getSuppliedRequest returns null per spec
            // however, only some application server like WebSphere actually implement it that way
            asyncContext.addListener(asyncListenerObjectPool.createInstance().withTransaction(transaction),
                asyncContext.getRequest(), asyncContext.getResponse());

            request.setAttribute(ASYNC_ATTRIBUTE, Boolean.TRUE);
            request.setAttribute(TRANSACTION_ATTRIBUTE, transaction);
        }
    }

    void recycle(JavaxApmAsyncListener apmAsyncListener) {
        asyncListenerObjectPool.recycle(apmAsyncListener);
    }
}
