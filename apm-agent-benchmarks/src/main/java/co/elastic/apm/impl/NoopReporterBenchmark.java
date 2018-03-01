package co.elastic.apm.impl;

import co.elastic.apm.impl.payload.TransactionPayload;
import co.elastic.apm.report.PayloadSender;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class NoopReporterBenchmark extends AbstractReporterBenchmark {

    /**
     * Convenience benchmark run method
     * <p>
     * For more accurate results, execute <code>mvn clean package</code> and run the benchmark via
     * <code>java -jar apm-agent-benchmarks/target/benchmarks.jar -prof gc</code>
     */
    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
            .include(NoopReporterBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build())
            .run();
    }

    protected PayloadSender getPayloadSender() {
        return new PayloadSender() {
            @Override
            public void sendPayload(TransactionPayload payload) {
                for (Transaction transaction : payload.getTransactions()) {
                    transaction.recycle();
                }
            }
        };
    }
}
