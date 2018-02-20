package co.elastic.apm.impl;

import co.elastic.apm.report.serialize.PayloadSerializer;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class HttpNoopJsonReporterBenchmark extends AbstractHttpReporterBenchmark {

    /**
     * Convenience benchmark run method
     * <p>
     * For more accurate results, execute <code>mvn clean package</code> and run the benchmark via
     * <code>java -jar apm-agent-benchmarks/target/benchmarks.jar -prof gc</code>
     */
    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
            .include(HttpNoopJsonReporterBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build())
            .run();
    }

    @Override
    protected PayloadSerializer getPayloadSerializer() {
        return (sink, payload) -> {
            sink.writeByte('{');
            sink.writeByte('}');
        };
    }
}
