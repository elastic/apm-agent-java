package co.elastic.apm.impl;

import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.payload.Agent;
import co.elastic.apm.impl.payload.Framework;
import co.elastic.apm.impl.payload.Language;
import co.elastic.apm.impl.payload.Process;
import co.elastic.apm.impl.payload.Runtime;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.payload.TransactionPayload;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.stacktrace.StacktraceFactory;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.ApmServerReporter;
import co.elastic.apm.report.PayloadSender;
import co.elastic.apm.report.Reporter;
import co.elastic.apm.report.ReporterConfiguration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@State(Scope.Benchmark)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(1)
public abstract class AbstractReporterBenchmark {

    public static final List<String> STRINGS = Arrays.asList("bar", "baz");
    protected TransactionPayload payload;
    protected ElasticApmTracer tracer;
    private Reporter reporter;
    private PayloadSender payloadSender;

    @Setup
    public void setUp() throws Exception {
        tracer = ElasticApmTracer.builder().reporter(reporter).stacktraceFactory(StacktraceFactory.Noop.INSTANCE).build();
        // in contrast to production configuration, do not drop transactions if the ring buffer is full
        // instead blocking wait until a slot becomes available
        // this is important because otherwise we would not measure the speed at which events can be handled
        // but rather how fast events get discarded
        payloadSender = getPayloadSender();
        Service service = new Service()
            .withName("java-test")
            .withVersion("1.0")
            .withEnvironment("test")
            .withAgent(new Agent("elastic-apm-java", "1.0.0"))
            .withRuntime(new Runtime("Java", "9.0.4"))
            .withFramework(new Framework("Servlet API", "3.1"))
            .withLanguage(new Language("Java", "9.0.4"));
        Process process = new Process()
            .withPid(2103)
            .withPpid(403L)
            .withTitle("/Library/Java/JavaVirtualMachines/jdk-9.0.4.jdk/Contents/Home/bin/java")
            .withArgv(Collections.singletonList("-javaagent:/path/to/elastic-apm-java.jar"));
        SystemInfo system = new SystemInfo("x86_64", "Felixs-MBP", "Mac OS X");
        ReporterConfiguration reporterConfiguration = new ReporterConfiguration();
        reporter = new ApmServerReporter(service, process, system, payloadSender, false, reporterConfiguration);
        payload = new TransactionPayload(process, service, system);
        for (int i = 0; i < reporterConfiguration.getMaxQueueSize(); i++) {
            Transaction t = new Transaction();
            t.start(tracer, 0);
            fillTransaction(t);
            payload.getTransactions().add(t);
        }
    }

    private void fillTransaction(Transaction t) {
        t.setName("GET /api/types");
        t.setType("request");
        t.withResult("success");
        t.withSampled(true);
        t.getSpanCount().getDropped().withTotal(2);

        Context context = t.getContext();
        Request request = context.getRequest();
        request.withHttpVersion("1.1");
        request.withMethod("POST");
        request.withRawBody("Hello World");
        request.getUrl()
            .withProtocol("https")
            .withFull("https://www.example.com/p/a/t/h?query=string#hash")
            .withHostname("www.example.com")
            .withPort("8080")
            .withPathname("/p/a/t/h")
            .withSearch("?query=string")
            .withRaw("/p/a/t/h?query=string)");
        request.getSocket()
            .withEncrypted(true)
            .withRemoteAddress("12.53.12.1");
        request.addHeader("user-agent", "Mozilla Chrome Edge");
        request.addHeader("content-type", "text/html");
        request.addHeader("cookie", "c1=v1; c2=v2");
        request.addHeader("some-other-header", "foo");
        request.addHeader("array", "foo, bar, baz");
        request.getCookies().put("c1", "v1");
        request.getCookies().put("c2", "v2");

        context.getResponse()
            .withStatusCode(200)
            .withFinished(true)
            .withHeadersSent(true)
            .addHeader("content-type", "application/json");

        context.getUser()
            .withId("99")
            .withUsername("foo")
            .withEmail("foo@example.com");

        context.getTags().put("organization_uuid", "9f0e9d64-c185-4d21-a6f4-4673ed561ec8");
        context.getCustom().put("my_key", 1);
        context.getCustom().put("some_other_value", "foo bar");
        context.getCustom().put("and_objects", STRINGS);

        Span span = new Span()
            .withName("SELECT FROM product_types")
            .withType("db.postgresql.query")
            .withStart(2.83092)
            .withDuration(3.781912);
        span.getContext().getDb()
            .withInstance("customers")
            .withStatement("SELECT * FROM product_types WHERE user_id=?")
            .withType("sql")
            .withUser("readonly_user");
        t.getSpans().add(span);
        t.getSpans().add(new Span()
            .withParent(0)
            .withName("GET /api/types")
            .withType("request")
            .withStart(0)
            .withDuration(32.592981));
        t.getSpans().add(new Span()
            .withParent(1)
            .withName("GET /api/types")
            .withType("request")
            .withStart(1.845)
            .withDuration(3.5642981));
        t.getSpans().add(new Span()
            .withParent(2)
            .withName("GET /api/types")
            .withType("request")
            .withStart(0)
            .withDuration(13.9802981));
    }

    protected abstract PayloadSender getPayloadSender();

    @TearDown
    public void tearDown() {
        reporter.close();
    }

    @Threads(Threads.MAX)
    @Benchmark
    public void testReport() {
        try (Transaction t = tracer.startTransaction()) {
            fillTransaction(t);
        }
    }

    @Benchmark
    @Threads(1)
    public void sendPayload() {
        payloadSender.sendPayload(payload);
    }
}
