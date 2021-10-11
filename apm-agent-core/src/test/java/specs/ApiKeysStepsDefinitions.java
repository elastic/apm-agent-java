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

import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ApiKeysStepsDefinitions {

    private ReporterConfiguration configuration = null;

    private final SpecMockServer server = new SpecMockServer("{{request.headers.Authorization}}");

    @Before
    public void init() {
        server.start();

        // we just initialize configuration as reporter is initialized lazily
        configuration = SpyConfiguration.createSpyConfig().getConfig(ReporterConfiguration.class);

        when(configuration.getServerUrls())
            .thenReturn(Collections.singletonList(server.getUrl()));
    }

    @After
    public void cleanup() {
        server.stop();
    }

    // API Key

    @When("an api key is set to {string} in the config")
    public void setApiKeyConfig(String value) {
        when(configuration.getApiKey())
            .thenReturn(value);
    }

    @Given("an api key is not set in the config")
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
        ApmServerClient apmServerClient = new ApmServerClient(configuration, null);
        apmServerClient.start();

        server.executeRequest(apmServerClient, (body) -> assertThat(body).isEqualTo(expectedHeaderValue));
    }

    private static URL buildUrl(String spec) {
        try {
            return new URL(spec);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

}
