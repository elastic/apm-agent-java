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
package co.elastic.apm.agent.objectpool;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TraceContextImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.objectpool.impl.BookkeeperObjectPool;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.Recyclable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extension of default pool factory that keeps track of all pools and thus allows to query their state while testing
 */
public class TestObjectPoolFactory extends ObjectPoolFactoryImpl {

    private final List<BookkeeperObjectPool<?>> createdPools = new ArrayList<BookkeeperObjectPool<?>>();
    private BookkeeperObjectPool<TransactionImpl> transactionPool;
    private BookkeeperObjectPool<SpanImpl> spanPool;
    private BookkeeperObjectPool<ErrorCaptureImpl> errorPool;
    private BookkeeperObjectPool<TraceContextImpl> spanLinksPool;

    @Override
    public <T extends Recyclable> ObservableObjectPool<T> createRecyclableObjectPool(int maxCapacity, Allocator<T> allocator) {
        ObservableObjectPool<T> pool = super.createRecyclableObjectPool(maxCapacity, allocator);
        BookkeeperObjectPool<T> wrappedPool = new BookkeeperObjectPool<>(pool);
        createdPools.add(wrappedPool);
        return wrappedPool;
    }

    public List<BookkeeperObjectPool<?>> getCreatedPools() {
        return createdPools;
    }

    public void checkAllPooledObjectsHaveBeenRecycled() {
        assertThat(createdPools)
            .describedAs("at least one object pool should have been created, test object pool factory likely not used whereas it should")
            .isNotEmpty();

        // retry loop added to prevent test failures due to asynchronous/un-synchronized pooling operations
        // this is fine because the actual failures we look for are when there is a span/transaction that has
        // not been recycled at all due to improper activation lifecycle.
        int retry = 5;
        boolean hasSomethingLeft;
        do {
            hasSomethingLeft = false;
            for (BookkeeperObjectPool<?> pool : createdPools) {
                Collection<?> toReturn = pool.getRecyclablesToReturn();
                hasSomethingLeft = hasSomethingLeft || toReturn.size() > 0;
            }
            if (hasSomethingLeft) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // silently ignored
                }
            }
        } while (--retry > 0 && hasSomethingLeft);

        if (retry == 0 && hasSomethingLeft) {
            for (BookkeeperObjectPool<?> pool : createdPools) {
                assertThat(pool.getRecyclablesToReturn())
                    .describedAs("pool should have all its items recycled")
                    .isEmpty();
            }
        }
    }

    public void reset() {
        createdPools.forEach(BookkeeperObjectPool::reset);
    }

    @Override
    public ObservableObjectPool<TransactionImpl> createTransactionPool(int maxCapacity, ElasticApmTracer tracer) {
        transactionPool = (BookkeeperObjectPool<TransactionImpl>) super.createTransactionPool(maxCapacity, tracer);
        return transactionPool;
    }

    @Override
    public ObservableObjectPool<SpanImpl> createSpanPool(int maxCapacity, ElasticApmTracer tracer) {
        spanPool = (BookkeeperObjectPool<SpanImpl>) super.createSpanPool(maxCapacity, tracer);
        return spanPool;
    }

    @Override
    public ObservableObjectPool<ErrorCaptureImpl> createErrorPool(int maxCapacity, ElasticApmTracer tracer) {
        errorPool = (BookkeeperObjectPool<ErrorCaptureImpl>) super.createErrorPool(maxCapacity, tracer);
        return errorPool;
    }

    @Override
    public ObservableObjectPool<TraceContextImpl> createSpanLinkPool(int maxCapacity, ElasticApmTracer tracer) {
        spanLinksPool = (BookkeeperObjectPool<TraceContextImpl>) super.createSpanLinkPool(maxCapacity, tracer);
        return spanLinksPool;
    }

    public BookkeeperObjectPool<TransactionImpl> getTransactionPool() {
        return transactionPool;
    }

    public BookkeeperObjectPool<SpanImpl> getSpanPool() {
        return spanPool;
    }

    public BookkeeperObjectPool<ErrorCaptureImpl> getErrorPool() {
        return errorPool;
    }

    public BookkeeperObjectPool<TraceContextImpl> getSpanLinksPool() {
        return spanLinksPool;
    }
}
