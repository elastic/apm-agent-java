package co.elastic.apm.agent.spring.webmvc;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.servlet.ServletInstrumentation;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

@RunWith(value = SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@TestConfiguration
public abstract class AbstractExceptionHandlerInstrumentationTest {

    protected static MockReporter reporter;
    protected static ElasticApmTracer tracer;
    protected MockMvc mockMvc;

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
            Arrays.asList(new ServletInstrumentation(tracer)));
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
        reporter.reset();
    }

    protected void assertExceptionCapture(Class exceptionClazz, MockHttpServletResponse response, int statusCode, String responseContent, String exceptionMessage) throws UnsupportedEncodingException {
        assertEquals(1, reporter.getTransactions().size());
        assertEquals(0, reporter.getSpans().size());
        assertEquals(1, reporter.getErrors().size());
        assertEquals(exceptionMessage, reporter.getErrors().get(0).getException().getMessage());
        assertEquals(exceptionClazz, reporter.getErrors().get(0).getException().getClass());
        assertEquals(statusCode, response.getStatus());
        assertEquals(responseContent, response.getContentAsString());
    }

}
