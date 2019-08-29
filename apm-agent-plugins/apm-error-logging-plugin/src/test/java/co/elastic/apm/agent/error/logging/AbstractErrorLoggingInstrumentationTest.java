package co.elastic.apm.agent.error.logging;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class AbstractErrorLoggingInstrumentationTest {

    protected static ElasticApmTracer tracer;
    protected static MockReporter reporter;

    @BeforeClass
    @BeforeAll
    public static void beforeAll() {
        reporter = new MockReporter();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(), Arrays.asList(new Slf4jLoggingInstrumentation()));
    }

    @AfterClass
    @AfterAll
    public static void afterAll() {
        ElasticApmAgent.reset();
    }

    @Before
    @BeforeEach
    public void startTransaction() {
        reporter.reset();
        tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
    }

    @After
    @AfterEach
    public void endTransaction() {
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
        reporter.reset();
    }

    void verifyThatExceptionCaptured(int errorCount, String exceptionMessage, Class exceptionClass) {
        assertEquals(errorCount, reporter.getErrors().size());
        Throwable exception = reporter.getErrors().get(0).getException();
        assertEquals(exceptionMessage, exception.getMessage());
        assertEquals(exceptionClass, exception.getClass());
    }
}
