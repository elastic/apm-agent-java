/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.servlet.ServletTransactionHelper;
import org.jctools.queues.atomic.AtomicQueueFactory;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletRequest;

import static co.elastic.apm.agent.servlet.ServletTransactionHelper.ASYNC_ATTRIBUTE;
import static co.elastic.apm.agent.servlet.ServletTransactionHelper.TRANSACTION_ATTRIBUTE;
import static co.elastic.apm.agent.servlet.helper.AsyncConstants.ASYNC_LISTENER_ADDED;
import static co.elastic.apm.agent.servlet.helper.AsyncConstants.MAX_POOLED_ELEMENTS;
import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

public class JakartaAsyncContextAdviceHelper implements AsyncContextAdviceHelper<AsyncContext> {

    private final ObjectPool<JakartaApmAsyncListener> asyncListenerObjectPool;
    private final ServletTransactionHelper servletTransactionHelper;
    private final Tracer tracer;

    public JakartaAsyncContextAdviceHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        servletTransactionHelper = new ServletTransactionHelper(tracer);

        asyncListenerObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<JakartaApmAsyncListener>newQueue(createBoundedMpmc(MAX_POOLED_ELEMENTS)),
            false,
            new JakartaAsyncContextAdviceHelper.JakartaApmAsyncListenerAllocator());
    }

    private final class JakartaApmAsyncListenerAllocator implements Allocator<JakartaApmAsyncListener> {
        @Override
        public JakartaApmAsyncListener createInstance() {
            return new JakartaApmAsyncListener(JakartaAsyncContextAdviceHelper.this);
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

    void recycle(JakartaApmAsyncListener apmAsyncListener) {
        asyncListenerObjectPool.recycle(apmAsyncListener);
    }
}
