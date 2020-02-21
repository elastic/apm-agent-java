package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.stagemonitor.util.IOUtils;

import java.net.URL;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class ConfigurationDefinitionReporterTest {

    @Rule
    public WireMockRule apmServer = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());
    private ConfigurationDefinitionReporter reporter;

    @Before
    public void setUp() throws Exception {
        apmServer.stubFor(get(urlEqualTo("/")).willReturn(okForJson(Map.of("version", "7.7.0"))));

        ReporterConfiguration config = SpyConfiguration.createSpyConfig().getConfig(ReporterConfiguration.class);
        List<URL> urls = List.of(new URL("http", "localhost", apmServer.port(), "/"));
        ApmServerClient apmServerClient = new ApmServerClient(config, urls);
        reporter = new ConfigurationDefinitionReporter(apmServerClient);
    }

    @Test
    public void testSuccess() {
        apmServer.stubFor(post("/config").willReturn(ok()));
        reporter.run();
        apmServer.verify(1, postRequestedFor(urlEqualTo("/config"))
            .withRequestBody(equalToJson(IOUtils.getResourceAsString("configuration.json"))));
    }

    @Test
    public void testNotFound() {
        apmServer.stubFor(post("/config").willReturn(notFound().withBody("Endpoint not found")));
        reporter.run();
        apmServer.verify(1, postRequestedFor(urlEqualTo("/config")));
    }

    @Test
    public void testBadRequest() {
        apmServer.stubFor(post("/config").willReturn(badRequest().withBody("Bad request")));
        reporter.run();
        apmServer.verify(1, postRequestedFor(urlEqualTo("/config")));
    }
}
