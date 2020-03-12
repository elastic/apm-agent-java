package specs;

import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class ApiKeysStepsDefinitions {

    private ApmServerClient apmServerClient;

    @Given("an agent")
    public void an_agent() {

        // so far an agent only mean an apm server client as API keys & security only has impact there
        ReporterConfiguration configuration = new ReporterConfiguration();
        apmServerClient = new ApmServerClient(configuration);
    }

    @When("an api key is set to {string} in the config")
    public void an_api_key_is_set_to_in_the_config(String string) {
        ReporterConfiguration configuration = new ReporterConfiguration();
        apmServerClient = new ApmServerClient(configuration);

        // Write code here that turns the phrase above into concrete actions
        throw new io.cucumber.java.PendingException();
    }

    @When("an api key is set in the config")
    public void an_api_key_is_set_in_the_config() {
        throw new io.cucumber.java.PendingException();
    }

    @Then("the Authorization header is {string}")
    public void the_Authorization_header_is(String string) {
        // Write code here that turns the phrase above into concrete actions
        throw new io.cucumber.java.PendingException();
    }


    @And("a secret_token is set in the config")
    public void a_secret_token_is_set_in_the_config() {
        // Write code here that turns the phrase above into concrete actions
        throw new io.cucumber.java.PendingException();
    }

    @Then("the api key is sent in the Authorization header")
    public void the_api_key_is_sent_in_the_Authorization_header() {
        // Write code here that turns the phrase above into concrete actions
        throw new io.cucumber.java.PendingException();
    }

    @And("an api key is not set in the config")
    public void an_api_key_is_not_set_in_the_config() {
        // Write code here that turns the phrase above into concrete actions
        throw new io.cucumber.java.PendingException();
    }

    @Then("the secret token is sent in the Authorization header")
    public void the_secret_token_is_sent_in_the_Authorization_header() {
        // Write code here that turns the phrase above into concrete actions
        throw new io.cucumber.java.PendingException();
    }
}
