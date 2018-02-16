package co.elastic.apm.reporter;

import co.elastic.apm.CpuProfiler;
import co.elastic.apm.report.serialize.JacksonPayloadSerializer;
import co.elastic.apm.report.serialize.PayloadSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import okio.Buffer;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public abstract class AbstractHttpJacksonReporterBenchmark extends AbstractHttpReporterBenchmark {

    /**
     * Convenience benchmark run method
     * <p>
     * For more accurate results, execute <code>mvn clean package</code> and run the benchmark via
     * <code>java -jar apm-agent-benchmarks/target/benchmarks.jar -prof gc</code>
     */
    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
            .include(AbstractHttpJacksonReporterBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .addProfiler(CpuProfiler.class)
            .build())
            .run();
    }

    @Setup
    public void setUp() throws Exception {
        super.setUp();
        Buffer buffer = new Buffer();
        getPayloadSerializer().serializePayload(buffer, payload);
        System.out.println("Size of payload in bytes: " + buffer.readByteString().toByteArray().length);
    }

    @Override
    protected PayloadSerializer getPayloadSerializer() {
        ObjectMapper objectMapper = getObjectMapper();
        objectMapper.registerModule(new AfterburnerModule());
        return new JacksonPayloadSerializer(objectMapper);
    }

    protected abstract ObjectMapper getObjectMapper();
}
