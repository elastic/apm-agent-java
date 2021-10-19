package co.elastic.apm.spring.boot.webflux;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.collections.WeakConcurrentProviderImpl;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.api.ElasticApm;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.stagemonitor.configuration.ConfigurationRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public abstract class AbstractWebfluxSpringBootTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractWebfluxSpringBootTest.class);

    @LocalServerPort
    private int port;

    private static MockReporter reporter;
    private static ConfigurationRegistry config;

    @BeforeAll
    static void beforeClass() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        config = mockInstrumentationSetup.getConfig();
        reporter = mockInstrumentationSetup.getReporter();
        ElasticApmAgent.initInstrumentation(mockInstrumentationSetup.getTracer(), ByteBuddyAgent.install());
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        Mockito.when(config.getConfig(ReporterConfiguration.class).isReportSynchronously()).thenReturn(true);

    }

    @AfterAll
    static void tearDown() {
        ElasticApmAgent.reset();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        Thread.sleep(500);
        reporter.reset();
    }

    @Test
    public void getMessageById() {
        Integer messageId = 1;
        Mono<HelloEntity> response = WebClientFactory
            .webClient(port).get()
            .uri("/" + messageId)
            .retrieve()
            .bodyToMono(HelloEntity.class);

        StepVerifier.create(response)
            .assertNext(k -> {
                assertThat(k.getMessage()).isEqualTo("hello elastic");
            })
            .verifyComplete();

        final Transaction transaction = getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo("TestApp#getMessageById");
        boolean isFinished = transaction.isFinished();
        logger.info("is finished = {}", isFinished);
        assertThat(transaction.getContext().getUser().getDomain()).isEqualTo("domain");
        assertThat(transaction.getContext().getUser().getId()).isEqualTo("id");
        assertThat(transaction.getContext().getUser().getEmail()).isEqualTo("email");
        assertThat(transaction.getContext().getUser().getUsername()).isEqualTo("username");

//        assertSpans("SELECT FROM hello", "SELECT hello.* FROM hello WHERE hello.id = $1 LIMIT 2");
    }


    @Test
    public void getAllMessages() {
        Flux<HelloEntity> response = WebClientFactory
            .webClient(port).get()
            .uri("/all")
            .retrieve()
            .bodyToFlux(HelloEntity.class);

        StepVerifier.create(response)
            .assertNext(k -> {
                assertThat(k.getMessage()).isEqualTo("hello elastic");
            })
            .assertNext(k -> {
                assertThat(k.getMessage()).isEqualTo("hello elastic 2");
            })
            .verifyComplete();

        final Transaction transaction = getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo("TestApp#getAllMessages");
        logger.info("is finished = {}", transaction.isFinished());

        assertThat(transaction.getContext().getUser().getDomain()).isEqualTo("domain");
        assertThat(transaction.getContext().getUser().getId()).isEqualTo("id");
        assertThat(transaction.getContext().getUser().getEmail()).isEqualTo("email");
        assertThat(transaction.getContext().getUser().getUsername()).isEqualTo("username");

//        assertSpans("SELECT FROM hello", "SELECT hello.* FROM hello");
    }

    protected Transaction getFirstTransaction() {
        return reporter.getFirstTransaction(200);
    }

    private void assertSpans(String expectedName, String expectedStatement) {
        reporter.awaitSpanCount(1);
        List<Span> spans = reporter.getSpans();
        assertThat(spans.size()).isEqualTo(1);
        Span firstSpan = spans.get(0);

        assertThat(firstSpan.getNameAsString()).isEqualTo(expectedName);
        assertThat(firstSpan.getType()).isEqualTo("db");
        assertThat(firstSpan.getSubtype()).isEqualTo("h2");
        assertThat(firstSpan.getAction()).isEqualTo("query");
        assertThat(firstSpan.getContext().getDb().getInstance()).isEqualTo("testdb");
        assertThat(firstSpan.getContext().getDb().getStatement()).isEqualTo(expectedStatement);
        assertThat(firstSpan.getContext().getDb().getType()).isEqualTo("sql");
        assertThat(firstSpan.getContext().getDb().getUser()).isEqualTo("sa");

        assertThat(firstSpan.getContext().getDestination().getAddress().toString()).isEqualTo("in-memory");
        assertThat(firstSpan.getContext().getDestination().getPort()).isEqualTo(-1);
        assertThat(firstSpan.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    @RestController
    @SpringBootApplication
    public static class TestApp {
        public static void main(String[] args) {
            SpringApplication.run(TestApp.class, args);
        }

        @Autowired
        private HelloRepository helloRepository;

        @GetMapping("/{id}")
        public Mono<HelloEntity> getMessageById(@PathVariable("id") Integer id) {
            logger.info("Trying to find by id = {}", id);
            setUserContext();
            return helloRepository.findById(id);
        }

        @GetMapping("/all")
        public Flux<HelloEntity> getAllMessages() {
            setUserContext();
            return helloRepository.findAll();
        }

        private void setUserContext() {
            ElasticApm.currentTransaction().setUser("id", "email", "username", "domain");
        }


    }

}
