/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package specs;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import io.cucumber.java.After;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.stagemonitor.configuration.ConfigurationRegistry;
import wiremock.com.fasterxml.jackson.core.JsonProcessingException;
import wiremock.com.fasterxml.jackson.databind.JsonNode;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

public class MockServerStepDefinitions {

    @Nullable
    private WireMockServer server;

    private final ScenarioState scenarioState;

    public MockServerStepDefinitions(ScenarioState scenarioState){
        this.scenarioState = scenarioState;
    }

    // lazy start
    private void startServer(){
        server = new WireMockServer(WireMockConfiguration.options()
            .extensions(new MockServerResponseTransformer())
            .dynamicPort());

        server.stubFor(get(urlEqualTo("/"))
            .willReturn(aResponse()
                .withTransformers(MockServerResponseTransformer.class.getName())));

        server.start();
    }

    @After
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    public URL getUrl(){
        try {
            return new URL(String.format("http://localhost:%d/", Objects.requireNonNull(server).port()));
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    @When("the agent sends a request to APM server")
    public void executeRequest() throws IOException {
        // lazy start
        if (server == null) {
            startServer();
        }
        scenarioState.setConfigOption("server_urls", getUrl().toString());
        ConfigurationRegistry config = scenarioState.getTracer().getConfigurationRegistry();
        ApmServerClient apmServerClient = new ApmServerClient(
            config.getConfig(ReporterConfiguration.class),
            config.getConfig(CoreConfiguration.class)
        );
        apmServerClient.start();
        try {
            apmServerClient.execute("/", new ApmServerClient.ConnectionHandler<>() {
                @Nullable
                @Override
                public Object withConnection(HttpURLConnection connection) throws IOException {
                    assertThat(connection.getResponseCode())
                        .describedAs("unexpected response code from server")
                        .isEqualTo(200);


                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = objectMapper.readTree(connection.getInputStream());
                    scenarioState.setApmServerResponse(jsonNode);
                    return null;
                }
            });
        } catch (Exception e) {
            fail(e.getMessage(), e);
        }
    }

    @Then("the {} header of the request is {string}")
    public void checkExpectedHeader(String headerName, String expectedHeaderValue) {
        JsonNode apmServerResponse = scenarioState.getApmServerResponse();
        assertThat(apmServerResponse.get(headerName).asText()).isEqualTo(expectedHeaderValue);
    }

    @Then("the {} header of the request matches regex {string}")
    public void theUserAgentHeaderMatchesRegex(String headerName, String regex) {
        JsonNode apmServerResponse = scenarioState.getApmServerResponse();
        assertThat(apmServerResponse.get(headerName).asText()).matches(regex);
    }

    private static class MockServerResponseTransformer extends ResponseTransformer {

        @Override
        public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String headersJson = objectMapper.writeValueAsString(request.getHeaders());
                return Response.response()
                    .body(headersJson)
                    .build();
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return Response.response()
                .status(500)
                .body("Failed to create response")
                .build();
        }

        @Override
        public String getName() {
            return getClass().getName();
        }
    }
}
