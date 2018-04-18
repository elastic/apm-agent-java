/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.benchmark.objectpool;

import co.elastic.apm.benchmark.AbstractBenchmark;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.objectpool.impl.BlockingQueueObjectPool;
import co.elastic.apm.objectpool.impl.MixedObjectPool;
import co.elastic.apm.objectpool.impl.RingBufferObjectPool;
import co.elastic.apm.objectpool.impl.ThreadLocalObjectPool;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.runner.RunnerException;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ObjectPoolBenchmark extends AbstractBenchmark {

    private RingBufferObjectPool<Transaction> ringBufferObjectPool;
    private BlockingQueueObjectPool<Transaction> blockingQueueObjectPool;
    private MixedObjectPool<Transaction> mixedObjectPool;
    private ThreadLocalObjectPool<Transaction> threadLocalObjectPool;

    public static void main(String[] args) throws RunnerException {
        run(ObjectPoolBenchmark.class);
    }

    @Setup
    public void setUp() {
        ringBufferObjectPool = new RingBufferObjectPool<>(256, true, Transaction::new);
        blockingQueueObjectPool = new BlockingQueueObjectPool<>(256, true, Transaction::new);
        mixedObjectPool = new MixedObjectPool<>(256, 1024, true, Transaction::new);
        threadLocalObjectPool = new ThreadLocalObjectPool<>(64, true, Transaction::new);
    }

    @TearDown
    public void tearDown() {
        System.out.println("Objects created by RingBufferObjectPool: " + ringBufferObjectPool.getGarbageCreated());
        System.out.println("Objects created by RingBufferObjectPool: " + ringBufferObjectPool.getGarbageCreated());
        System.out.println("Objects created by MixedObjectPool: " + mixedObjectPool.getGarbageCreated());
    }

    @Benchmark
    @Threads(8)
    public Transaction testNewOperator() {
        return new Transaction();
    }

    @Benchmark
    @Threads(8)
    public Transaction testRingBufferObjectPool() {
        Transaction transaction = ringBufferObjectPool.createInstance();
        ringBufferObjectPool.recycle(transaction);
        return transaction;
    }

    //    @Benchmark
    @Threads(8)
    public Transaction testBlockingQueueObjectPool() {
        Transaction transaction = blockingQueueObjectPool.createInstance();
        blockingQueueObjectPool.recycle(transaction);
        return transaction;
    }

    //    @Benchmark
    @Threads(8)
    public Transaction testMixedObjectPool() {
        Transaction transaction = mixedObjectPool.createInstance();
        mixedObjectPool.recycle(transaction);
        return transaction;
    }

    @Benchmark
    @Threads(8)
    public Transaction testThreadLocalObjectPool() {
        Transaction transaction = threadLocalObjectPool.createInstance();
        threadLocalObjectPool.recycle(transaction);
        return transaction;
    }

}
