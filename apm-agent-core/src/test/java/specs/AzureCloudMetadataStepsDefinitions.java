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

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class AzureCloudMetadataStepsDefinitions {

    // This is just a no-op implementation to make the test pass
    // proper implementation will be handled when implementing https://github.com/elastic/apm-agent-java/issues/1578

    @Given("an instrumented application is configured to collect cloud provider metadata for azure")
    public void application() {
    }

    @Given("the following environment variables are present")
    public void addEnvironmentVariable(DataTable envVariables) {
    }

    @When("cloud metadata is collected")
    public void cloudMetadataIsCollected() {
    }

    @Then("cloud metadata is not null")
    public void cloudMetadataIsNotNull() {
    }

    @And("cloud metadata {string} is {string}")
    public void cloudMetadataProperty(String name, String value) {
    }

    @Then("cloud metadata is null")
    public void cloudMetadataIsNull() {
    }

}
