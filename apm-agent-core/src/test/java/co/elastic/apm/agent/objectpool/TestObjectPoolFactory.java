/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.objectpool;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.impl.BookkeeperObjectPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extension of default pool factory that keeps track of all pools and thus allows to query their state while testing
 */
public class TestObjectPoolFactory extends ObjectPoolFactory {

    private final List<BookkeeperObjectPool<?>> createdPools = new ArrayList<BookkeeperObjectPool<?>>();
    private BookkeeperObjectPool<Transaction> transactionPool;
    private BookkeeperObjectPool<Span> spanPool;
    private BookkeeperObjectPool<ErrorCapture> errorPool;

    @Override
    protected <T extends Recyclable> ObjectPool<T> createRecyclableObjectPool(int maxCapacity, Allocator<T> allocator) {
        ObjectPool<T> pool = super.createRecyclableObjectPool(maxCapacity, allocator);
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
        for (BookkeeperObjectPool<?> pool : createdPools) {
            Collection<?> toReturn = pool.getRecyclablesToReturn();
            String pooledItemClass = toReturn.stream().findFirst().map(e -> e.getClass().getName()).orElse("?");
            assertThat(toReturn)
                .describedAs("pool should have all its items recycled : instance = %s, class = %s", toReturn, pooledItemClass)
                .isEmpty();
        }
    }

    public void reset() {
        createdPools.forEach(BookkeeperObjectPool::reset);
    }

    @Override
    public ObjectPool<Transaction> createTransactionPool(int maxCapacity, ElasticApmTracer tracer) {
        transactionPool = (BookkeeperObjectPool<Transaction>) super.createTransactionPool(maxCapacity, tracer);
        return transactionPool;
    }

    @Override
    public ObjectPool<Span> createSpanPool(int maxCapacity, ElasticApmTracer tracer) {
        spanPool = (BookkeeperObjectPool<Span>) super.createSpanPool(maxCapacity, tracer);
        return spanPool;
    }

    @Override
    public ObjectPool<ErrorCapture> createErrorPool(int maxCapacity, ElasticApmTracer tracer) {
        errorPool = (BookkeeperObjectPool<ErrorCapture>) super.createErrorPool(maxCapacity, tracer);
        return errorPool;
    }

    public BookkeeperObjectPool<Transaction> getTransactionPool() {
        return transactionPool;
    }

    public BookkeeperObjectPool<Span> getSpanPool() {
        return spanPool;
    }

    public BookkeeperObjectPool<ErrorCapture> getErrorPool() {
        return errorPool;
    }
}
