package co.elastic.apm.agent.spring.webmvc;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.servlet.ServletInstrumentation;
import co.elastic.apm.agent.spring.webmvc.testapp.common.CommonConfiguration;
import co.elastic.apm.agent.spring.webmvc.testapp.common.ExceptionServiceImpl;
import co.elastic.apm.agent.spring.webmvc.testapp.exception_resolver.ExceptionResolverController;
import co.elastic.apm.agent.spring.webmvc.testapp.exception_resolver.RestResponseStatusExceptionResolver;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@RunWith(value = SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
    CommonConfiguration.class,
    ExceptionResolverController.class,
    RestResponseStatusExceptionResolver.class,
    ExceptionServiceImpl.class})
@TestConfiguration
public class ExceptionHandlerInstrumentationWithExceptionResolverTest {

    private static MockReporter reporter;
    private static ElasticApmTracer tracer;
    private MockMvc mockMvc;

    @Autowired
    WebApplicationContext wac;

    @BeforeClass
    @BeforeAll
    public static void setUpAll() {
        reporter = new MockReporter();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(),
            Arrays.asList(new ServletInstrumentation(tracer), new ExceptionHandlerInstrumentation(tracer)));
    }

    @AfterClass
    @AfterAll
    public static void afterAll() {
        ElasticApmAgent.reset();
    }

    @Before
    @BeforeEach
    public void setup() {
        this.mockMvc =
            MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    public void testCallApiWithExceptionThrown() throws Exception {
        reporter.reset();

        this.mockMvc.perform(get("/exception-resolver"));

        assertEquals(1, reporter.getTransactions().size());
        assertEquals(0, reporter.getSpans().size());
        assertEquals(1, reporter.getErrors().size());
        assertEquals("runtime exception occured", reporter.getErrors().get(0).getException().getMessage());
    }
}
