package co.elastic.apm.agent.mdc;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.error.logging.Log4jLoggingInstrumentation;
import co.elastic.apm.agent.error.logging.Slf4jLoggingInstrumentation;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class MdcActivationListenerIT {

    private static final Logger logger = LoggerFactory.getLogger(MdcActivationListenerIT.class);
    private static final org.apache.logging.log4j.Logger apacheLogger = LogManager.getLogger(MdcActivationListenerIT.class);

    protected static ElasticApmTracer tracer;
    protected static MockReporter reporter;
    protected static ConfigurationRegistry config;
    private LoggingConfiguration loggingConfiguration;

    private Boolean log4jMdcWorking;

    @BeforeAll
    static void beforeAll() {
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(), Arrays.asList(new Slf4jLoggingInstrumentation(), new Log4jLoggingInstrumentation()));
    }

    @AfterAll
    static void afterAll() {
        ElasticApmAgent.reset();
    }

    @BeforeEach
    void setUp() throws Exception {
        MDC.clear();
        org.apache.log4j.MDC.clear();
        ThreadContext.clearAll();
        loggingConfiguration = config.getConfig(LoggingConfiguration.class);
    }

    @Test
    public void testVerifyThatWithEnabledCorrelationAndLoggedErrorMdcErrorIdIsNotBlankWithSlf4j() {
        log4jMdcWorking = true;
        assertMdcErrorIdIsEmpty();
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);

        Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader()).withType("request").withName("test");
        transaction.activate();
        logger.error("Some slf4j exception", new RuntimeException("Hello exception"));
        assertMdcErrorIdIsNotBlank();
        transaction.end();
    }

    @Test
    public void testVerifyThatWithEnabledCorrelationAndLoggedErrorMdcErrorIdIsNotBlankWithLog4j() {
        log4jMdcWorking = true;
        assertMdcErrorIdIsEmpty();
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);

        Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader()).withType("request").withName("test");
        transaction.activate();
        apacheLogger.error("Some apache logger exception", new RuntimeException("Hello exception"));

        assertMdcErrorIdIsNotBlank();
        transaction.end();
    }

    private void assertMdcErrorIdIsEmpty() {
        assertThat(MDC.get("error.id")).isNull();
        if (log4jMdcWorking == Boolean.TRUE) {
            assertThat(org.apache.log4j.MDC.get("error.id")).isNull();
        }
    }

    private void assertMdcErrorIdIsNotBlank() {
        assertThat(MDC.get("error.id")).isNotBlank();
        if (log4jMdcWorking == Boolean.TRUE) {
            assertThat(org.apache.log4j.MDC.get("error.id")).isNotNull();
        }
    }
}
