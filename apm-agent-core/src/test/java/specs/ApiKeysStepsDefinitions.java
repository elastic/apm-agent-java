package specs;

import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.HttpUtils;
import co.elastic.apm.agent.report.ReporterConfiguration;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.stagemonitor.configuration.ConfigurationOption;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApiKeysStepsDefinitions {

    // those constants should be provided by the gherkin test, not using hard-coded values
    // as it would require to update the test definition, we'll keep it as-is for now.
    private static final String SECRET_TOKEN = "secr3tT0ken";
    private static final String API_KEY = "@p1Key";

    // so far, only reporter and it's configuration is being tested
    private ReporterConfiguration configuration = null;

    private WireMockServer server = new WireMockServer(WireMockConfiguration.options()
        .extensions(new ResponseTemplateTransformer(false))
        .dynamicPort());

    @Before
    public void init() {
        server.stubFor(get(urlEqualTo("/"))
            .willReturn(aResponse()
                .withTransformers("response-template")
                // just send back auth header (if any) for easy parsing on client side
                .withBody("{{request.headers.Authorization}}")));

        server.start();
    }

    @After
    public void cleanup() {
        server.stop();
    }

    // Init

    @Given("an agent")
    public void initAgent() {
        // we just initialize configuration as reporter is initialized lazily
        configuration = mock(ReporterConfiguration.class);

        URL serverUrl = buildUrl(String.format("http://localhost:%d/", server.port()));

        when(configuration.getServerUrls())
            .thenReturn(Collections.singletonList(serverUrl));

        // just required to avoid NPEs
        when(configuration.getServerUrlsOption())
            .thenReturn(mock(ConfigurationOption.class));
        when(configuration.getServerTimeout())
            .thenReturn(TimeDuration.of("1s"));
    }

    // API Key

    @When("an api key is set to {string} in the config")
    public void setApiKeyConfig(String value) {
        when(configuration.getApiKey())
            .thenReturn(value);
    }

    @When("an api key is set in the config")
    public void setApiKey() {
        setApiKeyConfig(API_KEY);
    }

    @And("an api key is not set in the config")
    public void apiKeyNotSetInConfig() {
        // this is the default, thus there is nothing to do but to assert for it just in case
        assertThat(configuration.getApiKey())
            .isNull();
    }

    @Then("the api key is sent in the Authorization header")
    public void assertApiKeyInAuthorizationHeader() {
        checkExpectedHeader("ApiKey " + API_KEY);
    }

    // Secret token

    @And("a secret_token is set in the config")
    public void secretTokenSetInConfig() {
        setSecretToken(SECRET_TOKEN);
    }

    @Then("the secret token is sent in the Authorization header")
    public void secretTokenIsSent() {
        checkExpectedHeader("Bearer " + SECRET_TOKEN);
    }

    @When("a secret_token is set to {string} in the config")
    public void setSecretToken(String value) {
        when(configuration.getSecretToken())
            .thenReturn(value);
    }

    // Authorization header

    @Then("the Authorization header is {string}")
    public void checkExpectedHeader(String expectedHeaderValue)  {
        ApmServerClient apmServerClient = new ApmServerClient(configuration);

        try {
            apmServerClient.execute("/", new ApmServerClient.ConnectionHandler<Object>() {
                @Nullable
                @Override
                public Object withConnection(HttpURLConnection connection) throws IOException {
                    assertThat(connection.getResponseCode())
                        .describedAs("unexpected response code from server")
                        .isEqualTo(200);


                    String body = HttpUtils.readToString(connection.getInputStream());
                    assertThat(body).isEqualTo(expectedHeaderValue);

                    return null;
                }
            });
        } catch (Exception e) {
            fail(e.getMessage(), e);
        }
    }

    private static URL buildUrl(String spec) {
        try {
            return new URL(spec);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

}
