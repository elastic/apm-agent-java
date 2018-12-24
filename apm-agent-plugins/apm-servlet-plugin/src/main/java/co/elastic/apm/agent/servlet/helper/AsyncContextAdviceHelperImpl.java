/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.servlet.AsyncInstrumentation;
import co.elastic.apm.agent.servlet.ServletApiAdvice;
import co.elastic.apm.agent.servlet.ServletTransactionHelper;
import org.jctools.queues.atomic.AtomicQueueFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;

import static co.elastic.apm.agent.servlet.ServletTransactionHelper.ASYNC_ATTRIBUTE;
import static co.elastic.apm.agent.servlet.ServletTransactionHelper.TRANSACTION_ATTRIBUTE;
import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

public class AsyncContextAdviceHelperImpl implements AsyncInstrumentation.AsyncContextAdviceHelper<AsyncContext> {

    private static final String ASYNC_LISTENER_ADDED = ServletApiAdvice.class.getName() + ".asyncListenerAdded";

    private final ObjectPool<ApmAsyncListener> asyncListenerObjectPool;
    private final ServletTransactionHelper servletTransactionHelper;
    private final ElasticApmTracer tracer;

    public AsyncContextAdviceHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
        servletTransactionHelper = new ServletTransactionHelper(tracer);

        int maxPooledElements = tracer.getConfigurationRegistry().getConfig(ReporterConfiguration.class).getMaxQueueSize() * 2;
        asyncListenerObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<ApmAsyncListener>newQueue(createBoundedMpmc(maxPooledElements)),
            false,
            new ApmAsyncListenerAllocator());
    }

    ServletTransactionHelper getServletTransactionHelper() {
        return servletTransactionHelper;
    }

    private final class ApmAsyncListenerAllocator implements Allocator<ApmAsyncListener> {
        @Override
        public ApmAsyncListener createInstance() {
            return new ApmAsyncListener(AsyncContextAdviceHelperImpl.this);
        }
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

    void recycle(ApmAsyncListener apmAsyncListener) {
        asyncListenerObjectPool.recycle(apmAsyncListener);
    }
}
