package co.elastic.apm.agent.spring.webmvc.template.test;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.servlet.ServletInstrumentation;
import co.elastic.apm.agent.spring.webmvc.ViewRenderInstrumentation;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@TestConfiguration
public class AbstractViewRenderingInstrumentationTest extends AbstractInstrumentationTest {

    protected MockMvc mockMvc;

    @Autowired
    WebApplicationContext wac;

    @BeforeAll
    public static void setUpAll() {
        reporter = new MockReporter();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(), Arrays.asList(new ServletInstrumentation(tracer), new ViewRenderInstrumentation()));
    }

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }
}
