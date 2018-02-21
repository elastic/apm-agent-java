package co.elastic.apm.impl;

import co.elastic.apm.report.ApmServerReporter;
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
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@State(Scope.Benchmark)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(1)
public abstract class AbstractReporterBenchmark {

    public static final List<String> STRINGS = Arrays.asList("bar", "baz");
    private static final int PAYLOAD_SIZE = 250;
    protected TransactionPayload payload;
    private Reporter reporter;
    private PayloadSender payloadSender;
    protected ElasticApmTracer tracer;

    @Setup
    public void setUp() throws Exception {
        tracer = new ElasticApmTracer(ConfigurationRegistry.builder().build(), reporter);
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
            .withPpid(403)
            .withTitle("/Library/Java/JavaVirtualMachines/jdk-9.0.4.jdk/Contents/Home/bin/java")
            .withArgv(Collections.singletonList("-javaagent:/path/to/elastic-apm-java.jar"));
        SystemInfo system = new SystemInfo()
            .withArchitecture("x86_64")
            .withHostname("Felixs-MBP")
            .withPlatform("Mac OS X");
        reporter = new ApmServerReporter(service, process, system, payloadSender, false);
        payload = new TransactionPayload(service, process, system);
        for (int i = 0; i < PAYLOAD_SIZE; i++) {
            Transaction t = new Transaction();
            t.start(tracer, 0);
            fillTransaction(t);
            payload.getTransactions().add(t);
        }
    }

    private void fillTransaction(Transaction t) {
        t.setName("GET /api/types");
        t.setType("request");
        t.setDuration(32.592981);
        t.setResult("success");
        t.setSampled(true);
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
        request.getHeaders().put("user-agent", "Mozilla Chrome Edge");
        request.getHeaders().put("content-type", "text/html");
        request.getHeaders().put("cookie", "c1=v1; c2=v2");
        request.getHeaders().put("some-other-header", "foo");
        request.getHeaders().put("array", "foo, bar, baz");
        request.getCookies().put("c1", "v1");
        request.getCookies().put("c2", "v2");

        context.getResponse()
            .withStatusCode(200)
            .withFinished(true)
            .withHeadersSent(true)
            .getHeaders().put("content-type", "application/json");

        context.getUser()
            .withId("99")
            .withUsername("foo")
            .withEmail("foo@example.com");

        context.getTags().put("organization_uuid", "9f0e9d64-c185-4d21-a6f4-4673ed561ec8");
        context.getCustom().put("my_key", 1);
        context.getCustom().put("some_other_value", "foo bar");
        context.getCustom().put("and_objects", STRINGS);

        Span span = new Span()
            .withId(0)
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
            .withId(1)
            .withParent(0)
            .withName("GET /api/types")
            .withType("request")
            .withStart(0)
            .withDuration(32.592981));
        t.getSpans().add(new Span()
            .withId(2)
            .withParent(1)
            .withName("GET /api/types")
            .withType("request")
            .withStart(1.845)
            .withDuration(3.5642981));
        t.getSpans().add(new Span()
            .withId(3)
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
