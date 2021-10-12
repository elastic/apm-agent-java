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
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class UserAgentStepsDefinitions {

    private CoreConfiguration coreConfiguration = null;
    private ReporterConfiguration reporterConfiguration = null;

    private final SpecMockServer server = new SpecMockServer("{{request.headers.User-Agent}}");

    @Before
    public void init() {
        ConfigurationRegistry spyConfig = SpyConfiguration.createSpyConfig();

        coreConfiguration = spyConfig.getConfig(CoreConfiguration.class);
        reporterConfiguration = spyConfig.getConfig(ReporterConfiguration.class);

        server.start();

        when(reporterConfiguration.getServerUrls())
            .thenReturn(Collections.singletonList(server.getUrl()));
    }

    @After
    public void cleanup() {
        server.stop();
    }

    @When("service name is not set")
    public void serviceNameIsNotSet() {
        // In practice, for the Java agent we always have a default service name that is inferred from the JVM main class
        // thus this case is mostly to ensure proper coverage of the shared specification.
        when(coreConfiguration.getServiceName()).thenReturn("");
    }

    @When("service name is set to {string}")
    public void serviceNameSetTo(String name) {
        when(coreConfiguration.getServiceName()).thenReturn(name);
    }

    @When("service version is not set")
    public void serviceVersionIsNotSet() {
        when(coreConfiguration.getServiceVersion()).thenReturn("");
    }

    @When("service version is set to {string}")
    public void serviceVersionSetTo(String version) {
        when(coreConfiguration.getServiceVersion()).thenReturn(version);
    }

    @Then("the User-Agent header matches regex {string}")
    public void theUserAgentHeaderMatchesRegex(String regex) {
        ApmServerClient apmServerClient = new ApmServerClient(reporterConfiguration, coreConfiguration);
        apmServerClient.start();

        server.executeRequest(apmServerClient, (body) -> assertThat(body).matches(regex));


    }

}
