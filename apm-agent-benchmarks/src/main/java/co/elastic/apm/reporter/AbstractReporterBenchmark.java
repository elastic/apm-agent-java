package co.elastic.apm.reporter;

import co.elastic.apm.intake.Process;
import co.elastic.apm.intake.Service;
import co.elastic.apm.intake.transactions.Payload;
import co.elastic.apm.intake.transactions.Transaction;
import co.elastic.apm.report.PayloadSender;
import co.elastic.apm.report.Reporter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(1)
public abstract class AbstractReporterBenchmark {

    private Reporter reporter;
    private PayloadSender payloadSender;
    private Payload payload;

    @Setup
    public void setUp() {
        // in contrast to production configuration, do not drop transactions if the ring buffer is full
        // instead blocking wait until a slot becomes available
        // this is important because otherwise we would not measure the speed at which events can be handled
        // but rather how fast events get discarded
        payloadSender = getPayloadSender();
        reporter = new Reporter(new Service(), new Process(), new co.elastic.apm.intake.System(), payloadSender, false);
        payload = new Payload(new Service(), new Process(), new co.elastic.apm.intake.System());
        for (int i = 0; i < 250; i++) {
            payload.getTransactions().add(new Transaction());
        }
    }

    protected abstract PayloadSender getPayloadSender();

    @TearDown
    public void tearDown() {
        reporter.close();
        System.out.println("created transaction garbage: " + Transaction.transactionPool.getGarbageCreated());
    }

    @Threads(Threads.MAX)
    @Benchmark
    public void testReport() {
        reporter.report(Transaction.create());
    }

    @Benchmark
    @Threads(1)
    public void sendPayload(){
        payloadSender.sendPayload(payload);
    }
}
