/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package specs;

import co.elastic.apm.agent.configuration.SpyConfiguration;
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
import static org.mockito.Mockito.when;

public class ApiKeysStepsDefinitions {

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
        configuration = SpyConfiguration.createSpyConfig().getConfig(ReporterConfiguration.class);

        URL serverUrl = buildUrl(String.format("http://localhost:%d/", server.port()));

        when(configuration.getServerUrls())
            .thenReturn(Collections.singletonList(serverUrl));

    }

    // API Key

    @When("an api key is set to {string} in the config")
    public void setApiKeyConfig(String value) {
        when(configuration.getApiKey())
            .thenReturn(value);
    }

    @And("an api key is not set in the config")
    public void apiKeyNotSetInConfig() {
        // this is the default, thus there is nothing to do but to assert for it just in case
        assertThat(configuration.getApiKey())
            .isNull();
    }

    // Secret token

    @When("a secret_token is set to {string} in the config")
    public void setSecretToken(String value) {
        when(configuration.getSecretToken())
            .thenReturn(value);
    }

    // Authorization header

    @Then("the Authorization header is {string}")
    public void checkExpectedHeader(String expectedHeaderValue) {
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
