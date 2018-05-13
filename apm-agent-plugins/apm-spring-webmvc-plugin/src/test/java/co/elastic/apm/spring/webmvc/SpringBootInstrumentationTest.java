package co.elastic.apm.spring.webmvc;

import co.elastic.apm.MockReporter;
import co.elastic.apm.bci.ElasticApmAgent;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.web.WebConfiguration;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Collections;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class SpringBootInstrumentationTest {

    private MockReporter reporter;
    private ConfigurationRegistry config;
    @LocalServerPort
    private int port;
    private TestRestTemplate restTemplate;

    @Before
    public void setUp() {
        config = SpyConfiguration.createSpyConfig();
        reporter = new MockReporter();
        ElasticApmTracer tracer = ElasticApmTracer.builder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
        restTemplate = new TestRestTemplate(new RestTemplateBuilder().setConnectTimeout(0).setReadTimeout(0));
        reporter.reset();
    }

    @After
    public void tearDown() {
        ElasticApmAgent.reset();
    }

    @Test
    public void greetingShouldReturnDefaultMessage() throws Exception {
        assertThat(restTemplate.getForObject("http://localhost:" + port + "/", String.class))
            .contains("Hello World");

        // the transaction might not have been reported yet, as the http call returns when the ServletOutputStream has been closed,
        // which is before the transaction has ended
        assertThat(reporter.getFirstTransaction(500).getName().toString()).isEqualTo("HomeController#greeting");
    }

    @Test
    public void testStaticFile() throws Exception {
        when(config.getConfig(WebConfiguration.class).getIgnoreUrls()).thenReturn(Collections.emptyList());
        assertThat(restTemplate.getForObject("http://localhost:" + port + "/script.js", String.class))
            .contains("// empty test script");

        assertThat(reporter.getFirstTransaction(500).getName().toString()).isEqualTo("ResourceHttpRequestHandler");
    }

    @SpringBootApplication
    public static class Application {

        public static void main(String[] args) {
            SpringApplication.run(Application.class, args);
        }

        @RestController
        public static class HomeController {

            @GetMapping("/")
            public String greeting() {
                return "Hello World";
            }

        }
    }
}
