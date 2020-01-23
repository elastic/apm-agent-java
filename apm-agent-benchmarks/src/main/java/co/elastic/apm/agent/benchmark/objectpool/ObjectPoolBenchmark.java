/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.benchmark.objectpool;

import co.elastic.apm.agent.benchmark.AbstractBenchmark;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.objectpool.impl.ThreadLocalObjectPool;
import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.runner.RunnerException;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ObjectPoolBenchmark extends AbstractBenchmark {

    private ElasticApmTracer tracer;
    private ObjectPool<Transaction> blockingQueueObjectPool;
    private ObjectPool<Transaction> agronaQueueObjectPool;
    private ObjectPool<Transaction> threadLocalObjectPool;
    private ObjectPool<Transaction> jctoolsQueueObjectPool;
    private ObjectPool<Transaction> jctoolsAtomicQueueObjectPool;

    public static void main(String[] args) throws RunnerException {
        run(ObjectPoolBenchmark.class);
    }

    @Setup
    public void setUp() {
        tracer = new ElasticApmTracerBuilder().build();
        blockingQueueObjectPool = QueueBasedObjectPool.ofRecyclable(new ArrayBlockingQueue<>(256), true, () -> new Transaction(tracer));
        jctoolsQueueObjectPool = QueueBasedObjectPool.ofRecyclable(new MpmcArrayQueue<>(256), true, () -> new Transaction(tracer));
        jctoolsAtomicQueueObjectPool = QueueBasedObjectPool.ofRecyclable(new MpmcAtomicArrayQueue<>(256), true, () -> new Transaction(tracer));
        agronaQueueObjectPool = QueueBasedObjectPool.ofRecyclable(new ManyToManyConcurrentArrayQueue<>(256), true, () -> new Transaction(tracer));
        threadLocalObjectPool = new ThreadLocalObjectPool<>(64, true, () -> new Transaction(tracer));
    }

    @TearDown
    public void tearDown() {
        System.out.println("Objects created by agronaQueueObjectPool: " + agronaQueueObjectPool.getGarbageCreated());
    }

    //    @Benchmark
    @Threads(8)
    public Transaction testNewOperator() {
        return new Transaction(tracer);
    }

    @Benchmark
    @Threads(8)
    public Transaction testJctoolsAtomicQueueObjectPool() {
        Transaction transaction = jctoolsAtomicQueueObjectPool.createInstance();
        jctoolsAtomicQueueObjectPool.recycle(transaction);
        return transaction;
    }

    //    @Benchmark
    @Threads(8)
    public Transaction testArgonaQueueObjectPool() {
        Transaction transaction = agronaQueueObjectPool.createInstance();
        agronaQueueObjectPool.recycle(transaction);
        return transaction;
    }

    @Benchmark
    @Threads(8)
    public Transaction testJctoolsQueueObjectPool() {
        Transaction transaction = jctoolsQueueObjectPool.createInstance();
        jctoolsQueueObjectPool.recycle(transaction);
        return transaction;
    }

    //@Benchmark
    @Threads(8)
    public Transaction testBlockingQueueObjectPool() {
        Transaction transaction = blockingQueueObjectPool.createInstance();
        blockingQueueObjectPool.recycle(transaction);
        return transaction;
    }

    //    @Benchmark
    @Threads(8)
    public Transaction testThreadLocalObjectPool() {
        Transaction transaction = threadLocalObjectPool.createInstance();
        threadLocalObjectPool.recycle(transaction);
        return transaction;
    }

}
