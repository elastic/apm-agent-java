package co.elastic.apm.impl;

import co.elastic.apm.objectpool.impl.BlockingQueueObjectPool;
import co.elastic.apm.objectpool.impl.MixedObjectPool;
import co.elastic.apm.objectpool.impl.RingBufferObjectPool;
import co.elastic.apm.objectpool.impl.ThreadLocalObjectPool;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
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
