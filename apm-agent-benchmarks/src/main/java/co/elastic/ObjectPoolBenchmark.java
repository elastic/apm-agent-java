/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package co.elastic;

import co.elastic.apm.intake.transactions.Transaction;
import co.elastic.apm.objectpool.impl.BlockingQueueObjectPool;
import co.elastic.apm.objectpool.impl.MixedObjectPool;
import co.elastic.apm.objectpool.impl.RingBufferObjectPool;
import co.elastic.apm.objectpool.impl.ThreadLocalObjectPool;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.HotspotRuntimeProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
public class ObjectPoolBenchmark {

    private RingBufferObjectPool<Transaction> ringBufferObjectPool;
    private BlockingQueueObjectPool<Transaction> blockingQueueObjectPool;
    private MixedObjectPool<Transaction> mixedObjectPool;
    private ThreadLocalObjectPool<Transaction> threadLocalObjectPool;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(ObjectPoolBenchmark.class.getSimpleName())
            .warmupIterations(2)
            .measurementIterations(2)
            .forks(1)
            .addProfiler(GCProfiler.class)
            .addProfiler(HotspotRuntimeProfiler.class)
            .build();

        new Runner(opt).run();
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
