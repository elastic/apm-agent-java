package co.elastic.apm.reporter;

import co.elastic.apm.intake.Process;
import co.elastic.apm.intake.Service;
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
@Threads(Threads.MAX)
public abstract class AbstractReporterBenchmark {

    private Reporter reporter;

    @Setup
    public void setUp() {
        // in contrast to production configuration, do not drop transactions if the ring buffer is full
        // instead blocking wait until a slot becomes available
        // this is important because otherwise we would not measure the speed at which events can be handled
        // but rather how fast events get discarded
        reporter = new Reporter(new Service(), new Process(), new co.elastic.apm.intake.System(), getPayloadSender(), false);
    }

    protected abstract PayloadSender getPayloadSender();

    @TearDown
    public void tearDown() {
        reporter.close();
        System.out.println("created transaction garbage: " + Transaction.transactionPool.getGarbageCreated());
    }

    @Benchmark
    public void testReport() {
        reporter.report(Transaction.create());
    }
}
