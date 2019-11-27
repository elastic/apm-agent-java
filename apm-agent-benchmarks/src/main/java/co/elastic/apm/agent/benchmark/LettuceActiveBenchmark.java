package co.elastic.apm.agent.benchmark;

import co.elastic.apm.agent.impl.transaction.Transaction;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.runner.RunnerException;

public class LettuceActiveBenchmark extends LettuceBenchmark {

    public static void main(String[] args) throws RunnerException {
        run(LettuceActiveBenchmark.class);
    }

    public LettuceActiveBenchmark() {
        super(true);
    }

    @Benchmark
    public String benchmarkLettuce() {
        Transaction transaction = tracer.startRootTransaction(null).withName("transaction").activate();
        try {
            return sync.get("foo");
        } finally {
            transaction.deactivate().end();
        }
    }
}
